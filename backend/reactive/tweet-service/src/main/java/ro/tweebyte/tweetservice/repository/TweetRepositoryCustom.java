/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.UUID;

import reactor.core.publisher.Mono;

/**
 * Custom R2DBC repository fragment for the eager tweet reads. R2DBC has no
 * {@code @EntityGraph}, so loading a tweet together with its hashtag/mention relations is
 * expressed here as a single {@code DatabaseClient} join with an in-memory row aggregator,
 * mirroring the async stack's one-shot {@code @EntityGraph} fetch.
 *
 * @author Andrei Zbarcea
 */
public interface TweetRepositoryCustom {

	/**
	 * Owner-scoped eager read for the update diff: loads the tweet identified by
	 * {@code (id, userId)} together with its currently-linked hashtags and attached
	 * mentions in ONE SQL statement (two {@code LEFT JOIN}s), reconstructing the
	 * {@link TweetWithRelations} aggregate from the flat row set. Returns an empty
	 * {@link Mono} when no tweet matches the id/owner pair (the 404 gate), exactly as the
	 * former {@code findByIdAndUserId} + relation reads did.
	 * @param id the id of the tweet to load
	 * @param userId the id of the owning user (owner-scoping; non-owner → empty)
	 * @return the tweet and its relations, or empty when the tweet is missing/not owned
	 */
	Mono<TweetWithRelations> findWithRelationsByIdAndUserId(UUID id, UUID userId);

}
