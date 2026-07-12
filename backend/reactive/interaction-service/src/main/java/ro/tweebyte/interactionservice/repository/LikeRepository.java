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

import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.TweetCount;

@Repository
public interface LikeRepository extends ReactiveCrudRepository<LikeEntity, UUID> {

	@Query("SELECT * FROM likes WHERE user_id = :userId AND likeable_type = :likeableType "
			+ "ORDER BY id LIMIT :limit OFFSET :offset")
	Flux<LikeEntity> findByUserIdAndLikeableType(UUID userId, String likeableType, int limit, int offset);

	Mono<LikeEntity> findByUserIdAndLikeableIdAndLikeableType(UUID userId, UUID likeableId, String likeableType);

	@Query("SELECT * FROM likes WHERE likeable_id = :likeableId AND likeable_type = :likeableType "
			+ "ORDER BY id LIMIT :limit OFFSET :offset")
	Flux<LikeEntity> findByLikeableIdAndLikeableType(UUID likeableId, String likeableType, int limit, int offset);

	Mono<Long> countByLikeableIdAndLikeableType(UUID likeableId, String likeableType);

	@Query("SELECT likeable_id AS tweet_id, COUNT(*) AS total FROM likes "
			+ "WHERE likeable_id IN (:tweetIds) AND likeable_type = :likeableType GROUP BY likeable_id")
	Flux<TweetCount> countByLikeableIdInAndLikeableType(Collection<UUID> tweetIds, String likeableType);

	Mono<Void> deleteByUserIdAndLikeableIdAndLikeableType(UUID userId, UUID likeableId, String likeableType);

}
