/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.service.RecommendationService;

@RestController
@RequestMapping(path = "/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	@GetMapping(path = "/{userId}/follow")
	public Flux<UserDto> findFollowRecommendations(@PathVariable("userId") UUID userId) {
		return this.recommendationService.recommendUsersToFollow(userId);
	}

	@GetMapping(path = "/hashtags")
	public Flux<TweetDto.HashtagDto> findHashtagRecommendations() {
		return this.recommendationService.fetchPopularHashtags();
	}

}
