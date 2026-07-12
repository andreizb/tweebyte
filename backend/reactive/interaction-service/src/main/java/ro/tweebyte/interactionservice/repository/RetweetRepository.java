/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.TweetCount;

@Repository
public interface RetweetRepository extends ReactiveCrudRepository<RetweetEntity, UUID> {

	@Query("SELECT * FROM retweets WHERE retweeter_id = :userId "
			+ "ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
	Flux<RetweetEntity> findByRetweeterId(UUID userId, int limit, int offset);

	@Query("SELECT * FROM retweets WHERE original_tweet_id = :tweetId "
			+ "ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
	Flux<RetweetEntity> findByOriginalTweetId(UUID tweetId, int limit, int offset);

	Mono<Long> countByOriginalTweetId(UUID tweetId);

	@Query("SELECT original_tweet_id AS tweet_id, COUNT(*) AS total FROM retweets "
			+ "WHERE original_tweet_id IN (:tweetIds) GROUP BY original_tweet_id")
	Flux<TweetCount> countByOriginalTweetIdIn(Collection<UUID> tweetIds);

	@Query("SELECT DISTINCT unnest(media_ids) AS media_id FROM retweets WHERE media_ids IS NOT NULL")
	Flux<UUID> findReferencedMediaIds();

}
