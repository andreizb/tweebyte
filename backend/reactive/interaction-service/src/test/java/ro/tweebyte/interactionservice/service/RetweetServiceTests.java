/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetweetServiceTests {

	@InjectMocks
	private RetweetService retweetService;

	@Mock
	private TweetService tweetService;

	@Mock
	private RetweetRepository retweetRepository;

	@Mock
	private RetweetMapper retweetMapper;

	@Mock
	private UserService userService;

	@Mock
	private CountCache countCache;

	private final UUID originalTweetId = UUID.randomUUID();

	private final UUID retweetId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	private final RetweetCreateRequest createRequest = new RetweetCreateRequest();

	private final RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();

	private final RetweetEntity retweetEntity = new RetweetEntity();

	private final TweetDto tweetDto = new TweetDto();

	private final UserDto userDto = new UserDto();

	private final RetweetDto retweetDto = new RetweetDto();

	@BeforeEach
	void setUp() {
		this.createRequest.setOriginalTweetId(this.originalTweetId);
		this.updateRequest.setId(this.retweetId);
		this.updateRequest.setRetweeterId(this.userId);
		this.retweetEntity.setOriginalTweetId(this.retweetId);
		this.retweetEntity.setRetweeterId(this.userId);
	}

	@Test
	void createRetweet_Success() {
		given(this.retweetMapper.mapRequestToEntity(any(RetweetCreateRequest.class))).willReturn(this.retweetEntity);
		given(this.tweetService.getTweetSummary(any(UUID.class))).willReturn(Mono.just(this.tweetDto));
		given(this.retweetRepository.save(any(RetweetEntity.class))).willReturn(Mono.just(this.retweetEntity));
		given(this.retweetMapper.mapEntityToDto(any(RetweetEntity.class))).willReturn(this.retweetDto);
		given(this.countCache.increment(anyString())).willReturn(Mono.empty());
		StepVerifier.create(this.retweetService.createRetweet(this.createRequest))
			.expectNextMatches(Objects::nonNull)
			.verifyComplete();
	}

	@Test
	void updateRetweet_Success() {
		given(this.retweetRepository.findById(any(UUID.class))).willReturn(Mono.just(this.retweetEntity));
		given(this.retweetRepository.save(any(RetweetEntity.class))).willReturn(Mono.just(this.retweetEntity));
		doNothing().when(this.retweetMapper).mapRequestToEntity(this.updateRequest, this.retweetEntity);
		StepVerifier.create(this.retweetService.updateRetweet(this.updateRequest)).verifyComplete();
	}

	@Test
	void deleteRetweet_Success() {
		// deleteRetweet does findById + switchIfEmpty + flatMap(deleteById),
		// so the test stubs both findById (so the flatMap fires) and deleteById.
		given(this.retweetRepository.findById(any(UUID.class))).willReturn(Mono.just(this.retweetEntity));
		given(this.retweetRepository.deleteById(any(UUID.class))).willReturn(Mono.empty());
		given(this.countCache.decrement(anyString())).willReturn(Mono.empty());
		StepVerifier.create(this.retweetService.deleteRetweet(this.retweetId, this.userId)).verifyComplete();
	}

	@Test
	void deleteRetweet_NotOwner_Forbidden() {
		// A retweet owned by a different user yields 403 Forbidden — owner-scoped.
		RetweetEntity otherOwner = new RetweetEntity();
		otherOwner.setId(this.retweetId);
		otherOwner.setRetweeterId(UUID.randomUUID());
		given(this.retweetRepository.findById(any(UUID.class))).willReturn(Mono.just(otherOwner));
		StepVerifier.create(this.retweetService.deleteRetweet(this.retweetId, this.userId))
			.expectErrorMatches(t -> t instanceof org.springframework.web.server.ResponseStatusException rse
					&& rse.getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN)
			.verify();
	}

	@Test
	void deleteRetweet_NotFound_RaisesError() {
		// Missing-id path raises IllegalArgumentException instead of completing
		// silently, so the controller surfaces a non-2xx.
		given(this.retweetRepository.findById(any(UUID.class))).willReturn(Mono.empty());
		StepVerifier.create(this.retweetService.deleteRetweet(this.retweetId, this.userId))
			.expectErrorMatches(t -> t instanceof IllegalArgumentException)
			.verify();
	}

	@Test
	void getRetweetsByUser_Success() {
		given(this.tweetService.getTweetSummary(any(UUID.class))).willReturn(Mono.just(this.tweetDto));
		given(this.retweetRepository.findByRetweeterId(any(UUID.class), anyInt(), anyInt()))
			.willReturn(Flux.just(this.retweetEntity));
		given(this.userService.getUserSummary(any(UUID.class))).willReturn(Mono.just(this.userDto));
		given(this.retweetMapper.mapEntityToDto(any(RetweetEntity.class), any(UserDto.class), any(TweetDto.class)))
			.willReturn(this.retweetDto);

		StepVerifier.create(this.retweetService.getRetweetsByUser(this.userId, 0, 10))
			.expectNextMatches(dto -> dto.equals(this.retweetDto))
			.verifyComplete();
	}

	@Test
	void getRetweetsByUser_ResolvesUserSummaryOncePerCall() {
		// Every row's retweeterId equals the method parameter, so the retweeter
		// summary is resolved once and reused across all rows.
		RetweetEntity rowA = new RetweetEntity();
		rowA.setOriginalTweetId(UUID.randomUUID());
		rowA.setRetweeterId(this.userId);
		RetweetEntity rowB = new RetweetEntity();
		rowB.setOriginalTweetId(UUID.randomUUID());
		rowB.setRetweeterId(this.userId);

		given(this.retweetRepository.findByRetweeterId(any(UUID.class), anyInt(), anyInt()))
			.willReturn(Flux.just(rowA, rowB));
		given(this.tweetService.getTweetSummary(any(UUID.class))).willReturn(Mono.just(this.tweetDto));
		given(this.userService.getUserSummary(any(UUID.class))).willReturn(Mono.just(this.userDto));
		given(this.retweetMapper.mapEntityToDto(any(RetweetEntity.class), any(UserDto.class), any(TweetDto.class)))
			.willReturn(this.retweetDto);

		StepVerifier.create(this.retweetService.getRetweetsByUser(this.userId, 0, 10))
			.expectNext(this.retweetDto, this.retweetDto)
			.verifyComplete();

		verify(this.userService, times(1)).getUserSummary(this.userId);
	}

	@Test
	void getRetweetsOfTweet_Success() {
		given(this.retweetRepository.findByOriginalTweetId(any(UUID.class), anyInt(), anyInt()))
			.willReturn(Flux.just(this.retweetEntity));
		given(this.userService.getUserSummary(any(UUID.class))).willReturn(Mono.just(this.userDto));
		given(this.retweetMapper.mapEntityToDto(any(RetweetEntity.class), any(UserDto.class)))
			.willReturn(this.retweetDto);

		StepVerifier.create(this.retweetService.getRetweetsOfTweet(this.originalTweetId, 0, 10))
			.expectNextMatches(dto -> dto.equals(this.retweetDto))
			.verifyComplete();
	}

	@Test
	void getRetweetCountOfTweet_Success() {
		given(this.countCache.get(anyString(), any())).willAnswer(invocation -> invocation.getArgument(1));
		given(this.retweetRepository.countByOriginalTweetId(any(UUID.class))).willReturn(Mono.just(1L));
		StepVerifier.create(this.retweetService.getRetweetCountOfTweet(this.originalTweetId))
			.expectNext(1L)
			.verifyComplete();
	}

}
