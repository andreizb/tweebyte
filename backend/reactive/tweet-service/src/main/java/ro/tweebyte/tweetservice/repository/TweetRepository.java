/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.TweetEntity;

@Repository
public interface TweetRepository extends ReactiveCrudRepository<TweetEntity, UUID>, TweetRepositoryCustom {

	Mono<TweetEntity> findByIdAndUserId(UUID id, UUID userId);

	@Query("SELECT * FROM tweets WHERE user_id = :userId "
			+ "ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
	Flux<TweetEntity> findPageByUserId(UUID userId, int limit, long offset);

	// Unpaged feed for the recommender's user-summary: every tweet a user has
	// posted, newest first with id as a stable tiebreaker (same order as the
	// paginated PAGE_SORT). The recommender scores across the whole set, so it
	// must not be capped by a page size.
	Flux<TweetEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

	Flux<TweetEntity> findByUserIdIn(List<UUID> userIds, Pageable pageable);

	@Query("SELECT * FROM tweets WHERE :searchTerm <% content ORDER BY word_similarity(:searchTerm, content) DESC, created_at DESC, id DESC LIMIT :limit OFFSET :offset")
	Flux<TweetEntity> findBySimilarity(String searchTerm, int limit, int offset);

	// The join target is `tweet_hashtag` (singular) as defined in
	// deployment/docker-compose/schema/tweet_service_db.sql, matching the async
	// stack's repository — a divergent table name here would surface as
	// `bad SQL grammar` 500s on every hashtag search.
	@Query("SELECT t.* FROM tweets t JOIN tweet_hashtag th ON t.id = th.tweet_id JOIN hashtags h ON th.hashtag_id = h.id WHERE h.text = :searchTerm ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset")
	Flux<TweetEntity> findByHashtag(String searchTerm, int limit, int offset);

	// Distinct media_assets ids still referenced by any tweet. user-service's
	// stale-media GC unions this with its other reference sources so a tweet's
	// media is never swept (cross-DB, so it can't see these refs locally).
	@Query("SELECT DISTINCT unnest(media_ids) AS media_id FROM tweets WHERE media_ids IS NOT NULL")
	Flux<UUID> findReferencedMediaIds();

}
