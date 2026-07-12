/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.MentionEntity;

/**
 * {@link DatabaseClient} batched implementation of the {@code mentions} reconcile DML.
 * Every value is bound as a positional parameter (injection-safe) and each method
 * short-circuits on an empty collection. The insert supplies the client-generated id (the
 * {@code mentions.id} column has no DB default — guarded by commit {@code 6e31b41}), the
 * resolved user_id, the {@code @}-token text, and the owning tweet id, matching the
 * columns the per-row {@code save} populated. {@code replaceMentions} additionally folds
 * the delete and insert into one data-modifying CTE when both directions have work, so the
 * mention diff costs a single round-trip.
 *
 * @author Andrei Zbarcea
 */
@RequiredArgsConstructor
public class MentionRepositoryCustomImpl implements MentionRepositoryCustom {

	private final DatabaseClient databaseClient;

	@Override
	public Mono<Void> deleteByIdIn(Collection<UUID> mentionIds) {
		if (mentionIds.isEmpty()) {
			return Mono.empty();
		}
		List<UUID> ids = new ArrayList<>(mentionIds);
		String placeholders = IntStream.range(0, ids.size())
			.mapToObj(i -> ":id" + i)
			.collect(Collectors.joining(", "));
		DatabaseClient.GenericExecuteSpec spec = this.databaseClient
			.sql("DELETE FROM mentions WHERE id IN (" + placeholders + ")");
		for (int i = 0; i < ids.size(); i++) {
			spec = spec.bind("id" + i, ids.get(i));
		}
		return spec.then();
	}

	@Override
	public Mono<Void> insertAll(Collection<MentionEntity> mentions) {
		if (mentions.isEmpty()) {
			return Mono.empty();
		}
		List<MentionEntity> rows = new ArrayList<>(mentions);
		DatabaseClient.GenericExecuteSpec spec = this.databaseClient
			.sql("INSERT INTO mentions (id, user_id, text, tweet_id) VALUES " + insertValuesClause(rows));
		spec = bindInsertRows(spec, rows);
		return spec.then();
	}

	@Override
	public Mono<Void> replaceMentions(Collection<UUID> staleIds, Collection<MentionEntity> mentions) {
		if (mentions.isEmpty()) {
			return deleteByIdIn(staleIds);
		}
		if (staleIds.isEmpty()) {
			return insertAll(mentions);
		}
		// Both directions have work: fold the delete and the insert into one data-modifying
		// CTE so the diff is a single round-trip. The deleted ids (existing rows) and the
		// inserted ids (freshly generated UUIDs) are disjoint, so this writes exactly the
		// rows the deleteByIdIn + insertAll pair would, ordering-independently.
		List<UUID> stale = new ArrayList<>(staleIds);
		List<MentionEntity> rows = new ArrayList<>(mentions);
		String stalePlaceholders = IntStream.range(0, stale.size())
			.mapToObj(i -> ":del" + i)
			.collect(Collectors.joining(", "));
		DatabaseClient.GenericExecuteSpec spec = this.databaseClient
			.sql("WITH del AS (DELETE FROM mentions WHERE id IN (" + stalePlaceholders + ") RETURNING id) "
					+ "INSERT INTO mentions (id, user_id, text, tweet_id) VALUES " + insertValuesClause(rows));
		for (int i = 0; i < stale.size(); i++) {
			spec = spec.bind("del" + i, stale.get(i));
		}
		spec = bindInsertRows(spec, rows);
		return spec.then();
	}

	private static String insertValuesClause(List<MentionEntity> rows) {
		return IntStream.range(0, rows.size())
			.mapToObj(i -> "(:id" + i + ", :userId" + i + ", :text" + i + ", :tweetId" + i + ")")
			.collect(Collectors.joining(", "));
	}

	private static DatabaseClient.GenericExecuteSpec bindInsertRows(DatabaseClient.GenericExecuteSpec spec,
			List<MentionEntity> rows) {
		DatabaseClient.GenericExecuteSpec bound = spec;
		for (int i = 0; i < rows.size(); i++) {
			MentionEntity m = rows.get(i);
			// All four columns are NOT NULL: id + tweet_id are client-set (commit 6e31b41),
			// user_id is the resolved mention target, text the @-token. requireNonNull
			// documents the invariant DatabaseClient.bind relies on (it rejects nulls).
			UUID id = Objects.requireNonNull(m.getId(), "mention id must be set before insert");
			UUID userId = Objects.requireNonNull(m.getUserId(), "mention user_id must be set before insert");
			String text = Objects.requireNonNull(m.getText(), "mention text must be set before insert");
			UUID tweetId = Objects.requireNonNull(m.getTweetId(), "mention tweet_id must be set before insert");
			bound = bound.bind("id" + i, id)
				.bind("userId" + i, userId)
				.bind("text" + i, text)
				.bind("tweetId" + i, tweetId);
		}
		return bound;
	}

}
