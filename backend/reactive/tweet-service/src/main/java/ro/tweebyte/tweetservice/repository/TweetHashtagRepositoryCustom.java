/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;
import java.util.UUID;

import reactor.core.publisher.Mono;

/**
 * Custom fragment batching the {@code tweet_hashtag} join-table DML the tweet-update
 * reconcile emits. Replaces the former per-link {@code flatMap} delete/insert (one
 * statement per hashtag) with a single multi-row statement each, the R2DBC equivalent of
 * the async stack's Hibernate-batched collection-diff flush.
 *
 * @author Andrei Zbarcea
 */
public interface TweetHashtagRepositoryCustom {

	/**
	 * Delete the join rows linking {@code tweetId} to each of the given hashtag ids in one
	 * {@code DELETE ... WHERE tweet_id = ? AND hashtag_id IN (...)} statement. No-op (empty
	 * {@link Mono}) when {@code hashtagIds} is empty.
	 * @param tweetId the tweet whose links are being pruned
	 * @param hashtagIds the hashtag ids whose links should be removed
	 * @return a completion signal that finishes once the links are deleted
	 */
	Mono<Void> deleteLinks(UUID tweetId, Collection<UUID> hashtagIds);

	/**
	 * Insert one join row per hashtag id linking it to {@code tweetId}, in a single
	 * multi-row {@code INSERT INTO tweet_hashtag (tweet_id, hashtag_id) VALUES (..),(..)}.
	 * No-op (empty {@link Mono}) when {@code hashtagIds} is empty.
	 * @param tweetId the tweet the hashtags are being linked to
	 * @param hashtagIds the hashtag ids to link
	 * @return a completion signal that finishes once the links are inserted
	 */
	Mono<Void> insertLinks(UUID tweetId, Collection<UUID> hashtagIds);

	/**
	 * Reconcile a tweet's hashtag links in a single round-trip: prune the stale links and
	 * add the new ones together. When both sides are non-empty the prune and the add are
	 * folded into one data-modifying CTE
	 * ({@code WITH del AS (DELETE ... RETURNING ...) INSERT ...}) so the diff costs one
	 * statement instead of a {@code deleteLinks} then {@code insertLinks} pair. When only
	 * one side has work it degrades to that single statement, and it is a no-op (empty
	 * {@link Mono}) when both are empty.
	 *
	 * <p>
	 * The stale and new ids are disjoint by construction (a hashtag is stale iff its text
	 * left the desired set, new iff it joined it — partitioned by text, so never the same
	 * id), so the combined statement deletes and inserts disjoint {@code (tweet_id,
	 * hashtag_id)} keys: identical final rows to the two separate statements, with no PK
	 * conflict and no dependence on statement ordering within the CTE.
	 * @param tweetId the tweet whose links are being reconciled
	 * @param staleHashtagIds the hashtag ids whose links should be removed
	 * @param newHashtagIds the hashtag ids whose links should be added
	 * @return a completion signal that finishes once the links are reconciled
	 */
	Mono<Void> replaceLinks(UUID tweetId, Collection<UUID> staleHashtagIds, Collection<UUID> newHashtagIds);

}
