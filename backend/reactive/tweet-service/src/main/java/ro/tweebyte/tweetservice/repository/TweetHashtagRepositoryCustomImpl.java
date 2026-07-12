/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/**
 * {@link DatabaseClient} batched implementation of the {@code tweet_hashtag} reconcile
 * DML. Every value is bound as a positional parameter (no string concatenation of ids) so
 * the statements are injection-safe, and each short-circuits to an empty {@link Mono} on
 * an empty id list rather than emitting invalid SQL. {@code replaceLinks} additionally
 * folds the prune and add into one data-modifying CTE when both directions have work, so
 * the link diff costs a single round-trip.
 *
 * @author Andrei Zbarcea
 */
@RequiredArgsConstructor
public class TweetHashtagRepositoryCustomImpl implements TweetHashtagRepositoryCustom {

	private final DatabaseClient databaseClient;

	@Override
	public Mono<Void> deleteLinks(UUID tweetId, Collection<UUID> hashtagIds) {
		if (hashtagIds.isEmpty()) {
			return Mono.empty();
		}
		List<UUID> ids = new ArrayList<>(hashtagIds);
		String placeholders = IntStream.range(0, ids.size())
			.mapToObj(i -> ":h" + i)
			.collect(Collectors.joining(", "));
		DatabaseClient.GenericExecuteSpec spec = this.databaseClient
			.sql("DELETE FROM tweet_hashtag WHERE tweet_id = :tweetId AND hashtag_id IN (" + placeholders + ")")
			.bind("tweetId", tweetId);
		for (int i = 0; i < ids.size(); i++) {
			spec = spec.bind("h" + i, ids.get(i));
		}
		return spec.then();
	}

	@Override
	public Mono<Void> insertLinks(UUID tweetId, Collection<UUID> hashtagIds) {
		if (hashtagIds.isEmpty()) {
			return Mono.empty();
		}
		List<UUID> ids = new ArrayList<>(hashtagIds);
		DatabaseClient.GenericExecuteSpec spec = this.databaseClient
			.sql("INSERT INTO tweet_hashtag (tweet_id, hashtag_id) VALUES " + insertValuesClause(ids))
			.bind("tweetId", tweetId);
		spec = bindNewLinkIds(spec, ids);
		return spec.then();
	}

	@Override
	public Mono<Void> replaceLinks(UUID tweetId, Collection<UUID> staleHashtagIds, Collection<UUID> newHashtagIds) {
		if (newHashtagIds.isEmpty()) {
			return deleteLinks(tweetId, staleHashtagIds);
		}
		if (staleHashtagIds.isEmpty()) {
			return insertLinks(tweetId, newHashtagIds);
		}
		// Both directions have work: fold the prune and the add into one data-modifying CTE
		// so the diff is a single round-trip. The deleted (stale) and inserted (new) join
		// keys are disjoint (partitioned by hashtag text), so this writes exactly the rows
		// the deleteLinks + insertLinks pair would, ordering-independently.
		List<UUID> stale = new ArrayList<>(staleHashtagIds);
		List<UUID> fresh = new ArrayList<>(newHashtagIds);
		String stalePlaceholders = IntStream.range(0, stale.size())
			.mapToObj(i -> ":s" + i)
			.collect(Collectors.joining(", "));
		DatabaseClient.GenericExecuteSpec spec = this.databaseClient
			.sql("WITH del AS (DELETE FROM tweet_hashtag WHERE tweet_id = :tweetId AND hashtag_id IN ("
					+ stalePlaceholders + ") RETURNING hashtag_id) "
					+ "INSERT INTO tweet_hashtag (tweet_id, hashtag_id) VALUES " + insertValuesClause(fresh))
			.bind("tweetId", tweetId);
		for (int i = 0; i < stale.size(); i++) {
			spec = spec.bind("s" + i, stale.get(i));
		}
		spec = bindNewLinkIds(spec, fresh);
		return spec.then();
	}

	private static String insertValuesClause(List<UUID> ids) {
		return IntStream.range(0, ids.size())
			.mapToObj(i -> "(:tweetId, :h" + i + ")")
			.collect(Collectors.joining(", "));
	}

	private static DatabaseClient.GenericExecuteSpec bindNewLinkIds(DatabaseClient.GenericExecuteSpec spec,
			List<UUID> ids) {
		DatabaseClient.GenericExecuteSpec bound = spec;
		for (int i = 0; i < ids.size(); i++) {
			bound = bound.bind("h" + i, ids.get(i));
		}
		return bound;
	}

}
