/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;
import java.util.UUID;

import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.MentionEntity;

/**
 * Custom fragment batching the {@code mentions} DML the tweet-update reconcile emits.
 * Replaces the former per-mention {@code deleteById}/{@code save} (one statement each)
 * with a single multi-row statement per direction.
 *
 * @author Andrei Zbarcea
 */
public interface MentionRepositoryCustom {

	/**
	 * Delete every mention whose id is in {@code mentionIds} in one
	 * {@code DELETE ... WHERE id IN (...)} statement. No-op (empty {@link Mono}) when
	 * {@code mentionIds} is empty.
	 * @param mentionIds the ids of the mentions to delete
	 * @return a completion signal that finishes once the mentions are deleted
	 */
	Mono<Void> deleteByIdIn(Collection<UUID> mentionIds);

	/**
	 * Insert every mention in {@code mentions} (each carrying a client-generated id +
	 * user_id + text + tweet_id) in one multi-row
	 * {@code INSERT INTO mentions (id, user_id, text, tweet_id) VALUES (..),(..)}. No-op
	 * (empty {@link Mono}) when {@code mentions} is empty.
	 * @param mentions the mention entities to insert, each fully populated
	 * @return a completion signal that finishes once the mentions are inserted
	 */
	Mono<Void> insertAll(Collection<MentionEntity> mentions);

	/**
	 * Reconcile a tweet's mentions in a single round-trip: drop the stale rows and insert
	 * the new ones together. When both sides are non-empty they are folded into one
	 * data-modifying CTE ({@code WITH del AS (DELETE ... RETURNING id) INSERT ...}) so the
	 * diff costs one statement instead of a {@code deleteByIdIn} then {@code insertAll}
	 * pair. When only one side has work it degrades to that single statement, and it is a
	 * no-op (empty {@link Mono}) when both are empty.
	 *
	 * <p>
	 * Deleted ids (existing rows) and inserted ids (freshly client-generated UUIDs) are
	 * always disjoint, so the combined statement deletes and inserts disjoint primary keys:
	 * identical final rows to the two separate statements, with no PK conflict and no
	 * dependence on statement ordering within the CTE.
	 * @param staleIds the ids of the mentions to delete
	 * @param mentions the mention entities to insert, each fully populated
	 * @return a completion signal that finishes once the mentions are reconciled
	 */
	Mono<Void> replaceMentions(Collection<UUID> staleIds, Collection<MentionEntity> mentions);

}
