/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch coverage for RetweetService — covers createRetweet media validation (valid +
 * missing), the updateRetweet not-found and not-owner arms, and the batched count read.
 */
@ExtendWith(MockitoExtension.class)
class RetweetServiceBranchTests {

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

	private UUID originalTweetId;

	private UUID retweetId;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.originalTweetId = UUID.randomUUID();
		this.retweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();
	}

	@Test
	void createRetweet_WithValidMedia_ValidatesThenSaves() {
		UUID mediaId = UUID.randomUUID();
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(this.originalTweetId);
		request.setMediaIds(new UUID[] { mediaId });
		RetweetEntity entity = new RetweetEntity();
		entity.setOriginalTweetId(this.originalTweetId);

		given(this.tweetService.getTweetSummary(this.originalTweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(Mono.just(true));
		given(this.retweetMapper.mapRequestToEntity(request)).willReturn(entity);
		given(this.retweetRepository.save(entity)).willReturn(Mono.just(entity));
		given(this.countCache.increment(anyString())).willReturn(Mono.empty());
		given(this.retweetMapper.mapEntityToDto(entity)).willReturn(new RetweetDto());

		StepVerifier.create(this.retweetService.createRetweet(request)).expectNextCount(1).verifyComplete();

		verify(this.userService).mediaExists(mediaId);
		verify(this.retweetRepository).save(entity);
	}

	@Test
	void createRetweet_WithMissingMedia_ErrorsBadRequestAndDoesNotSave() {
		UUID mediaId = UUID.randomUUID();
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(this.originalTweetId);
		request.setMediaIds(new UUID[] { mediaId });
		RetweetEntity entity = new RetweetEntity();
		entity.setOriginalTweetId(this.originalTweetId);

		given(this.tweetService.getTweetSummary(this.originalTweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(Mono.just(false));
		// validateMediaIds(...).then(save(...)) assembles the save publisher eagerly, so
		// the mapper/repo must return non-null even though the error short-circuits before
		// the save is ever subscribed.
		given(this.retweetMapper.mapRequestToEntity(request)).willReturn(entity);
		given(this.retweetRepository.save(entity)).willReturn(Mono.just(entity));

		StepVerifier.create(this.retweetService.createRetweet(request))
			.expectErrorMatches(e -> e instanceof ResponseStatusException rse
					&& rse.getStatusCode() == HttpStatus.BAD_REQUEST)
			.verify();

		verify(this.countCache, never()).increment(any());
	}

	@Test
	void updateRetweet_NotFound_RaisesIllegalArgument() {
		RetweetUpdateRequest request = new RetweetUpdateRequest();
		request.setId(this.retweetId);
		request.setRetweeterId(this.userId);
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Mono.empty());

		StepVerifier.create(this.retweetService.updateRetweet(request))
			.expectErrorMatches(e -> e instanceof IllegalArgumentException
					&& "Retweet does not exist.".equals(e.getMessage()))
			.verify();

		verify(this.retweetRepository, never()).save(any());
	}

	@Test
	void updateRetweet_NotOwner_RaisesForbidden() {
		RetweetUpdateRequest request = new RetweetUpdateRequest();
		request.setId(this.retweetId);
		request.setRetweeterId(this.userId);
		RetweetEntity otherOwner = new RetweetEntity();
		otherOwner.setId(this.retweetId);
		otherOwner.setRetweeterId(UUID.randomUUID());
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Mono.just(otherOwner));

		StepVerifier.create(this.retweetService.updateRetweet(request))
			.expectErrorMatches(e -> e instanceof ResponseStatusException rse
					&& rse.getStatusCode() == HttpStatus.FORBIDDEN)
			.verify();

		verify(this.retweetRepository, never()).save(any());
	}

	@Test
	void updateRetweet_Owner_Saves() {
		RetweetUpdateRequest request = new RetweetUpdateRequest();
		request.setId(this.retweetId);
		request.setRetweeterId(this.userId);
		RetweetEntity owned = new RetweetEntity();
		owned.setId(this.retweetId);
		owned.setRetweeterId(this.userId);
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Mono.just(owned));
		doNothing().when(this.retweetMapper).mapRequestToEntity(request, owned);
		given(this.retweetRepository.save(owned)).willReturn(Mono.just(owned));

		StepVerifier.create(this.retweetService.updateRetweet(request)).verifyComplete();

		verify(this.retweetRepository).save(owned);
	}

	@Test
	void updateRetweet_NotFound_NotOwnerError_DoesNotDecrement() {
		// deleteRetweet not-found arm: confirms no decrement on a missing id.
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Mono.empty());

		StepVerifier.create(this.retweetService.deleteRetweet(this.retweetId, this.userId))
			.expectError(IllegalArgumentException.class)
			.verify();

		verify(this.countCache, never()).decrement(anyString());
	}

	@Test
	void getRetweetCountsForTweets_DelegatesToCacheGetAll() {
		List<UUID> ids = List.of(this.originalTweetId);
		Map<UUID, Long> expected = Map.of(this.originalTweetId, 6L);
		given(this.countCache.getAll(any(), any(), any())).willReturn(Mono.just(expected));

		StepVerifier.create(this.retweetService.getRetweetCountsForTweets(ids)).expectNext(expected).verifyComplete();

		verify(this.countCache).getAll(any(), any(), any());
	}

	@Test
	void createRetweet_emptyMediaIds_skipsMediaValidationAndProceedsSave() {
		// Branch: mediaIds.length == 0 → validateMediaIds returns Mono.empty() immediately.
		// Tests the second condition of the compound OR in validateMediaIds.
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setRetweeterId(this.userId);
		request.setOriginalTweetId(this.originalTweetId);
		request.setMediaIds(new UUID[0]); // non-null but empty → early return

		RetweetEntity entity = new RetweetEntity();
		entity.setId(this.retweetId);
		entity.setOriginalTweetId(this.originalTweetId);
		entity.setRetweeterId(this.userId);
		RetweetDto dto = new RetweetDto();

		given(this.tweetService.getTweetSummary(this.originalTweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.retweetMapper.mapRequestToEntity(request)).willReturn(entity);
		given(this.retweetRepository.save(entity)).willReturn(Mono.just(entity));
		given(this.countCache.increment(any())).willReturn(Mono.empty());
		given(this.retweetMapper.mapEntityToDto(entity)).willReturn(dto);

		StepVerifier.create(this.retweetService.createRetweet(request))
			.assertNext(result -> assertThat(result).isNotNull())
			.verifyComplete();
	}

	@Test
	void getRetweetCountsForTweets_CacheMiss_LoaderQueriesGroupByAndCollectsTotals() {
		// Drive the real GROUP BY loader the service hands to getAll: countByOriginalTweetIdIn →
		// collectMap(tweetId, total), so the loader lambda itself is exercised, not just delegation.
		UUID otherId = UUID.randomUUID();
		List<UUID> ids = List.of(this.originalTweetId, otherId);
		given(this.retweetRepository.countByOriginalTweetIdIn(ids))
			.willReturn(Flux.just(new TweetCount(this.originalTweetId, 6L), new TweetCount(otherId, 2L)));
		given(this.countCache.getAll(any(), any(), any())).willAnswer(invocation -> {
			Function<List<UUID>, Mono<Map<UUID, Long>>> loader = invocation.getArgument(2);
			return loader.apply(ids);
		});

		StepVerifier.create(this.retweetService.getRetweetCountsForTweets(ids))
			.assertNext(counts -> assertThat(counts).containsEntry(this.originalTweetId, 6L).containsEntry(otherId, 2L))
			.verifyComplete();
	}

}
