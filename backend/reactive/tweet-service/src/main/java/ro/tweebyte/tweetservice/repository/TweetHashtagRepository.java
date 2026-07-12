/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.TweetHashtagEntity;

public interface TweetHashtagRepository
		extends ReactiveCrudRepository<TweetHashtagEntity, UUID>, TweetHashtagRepositoryCustom {

	Mono<Void> deleteByTweetId(UUID tweetId);

	Flux<TweetHashtagEntity> findByTweetId(UUID tweetId);

}
