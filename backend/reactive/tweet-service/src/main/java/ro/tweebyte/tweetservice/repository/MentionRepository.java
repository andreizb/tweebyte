/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.MentionEntity;

@Repository
public interface MentionRepository extends ReactiveCrudRepository<MentionEntity, UUID>, MentionRepositoryCustom {

	@Query("SELECT * FROM mentions WHERE tweet_id = :tweetId ORDER BY text, id")
	Flux<MentionEntity> findMentionsByTweetId(UUID tweetId);

	@Query("SELECT * FROM mentions WHERE tweet_id IN (:tweetIds) ORDER BY tweet_id, text, id")
	Flux<MentionEntity> findByTweetIdIn(Collection<UUID> tweetIds);

	Mono<Void> deleteByTweetId(UUID tweetId);

}
