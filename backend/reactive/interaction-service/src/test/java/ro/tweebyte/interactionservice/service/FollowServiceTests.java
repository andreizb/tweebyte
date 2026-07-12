/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
class FollowServiceTests {

	private FollowService followService;

	@Mock
	private UserService userService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private FollowMapper followMapper;

	@Mock
	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, byte[]> valueOperations;

	@Mock
	private CountCache countCache;

	@Mock
	private TweetInteractionsService tweetInteractionsService;

	private final UUID userId = UUID.randomUUID();

	private final UUID followedId = UUID.randomUUID();

	private final String userName = "testUser";

	private final UserDto userDto = new UserDto();

	private final FollowDto followDto = new FollowDto();

	@BeforeEach
	void init() {
		this.userDto.setId(this.userId);
		this.userDto.setUserName(this.userName);

		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		this.followService = new FollowService(this.userService, this.followRepository, this.followMapper,
				this.redisTemplate, this.countCache, this.tweetInteractionsService);
		ReflectionTestUtils.setField(this.followService, "self", this.followService);
	}

	@Test
	void getFollowersCount_Success() {
		given(this.countCache.get(anyString(), any())).willAnswer(invocation -> invocation.getArgument(1));
		given(this.followRepository.countByFollowedIdAndStatus(any(UUID.class), eq("ACCEPTED")))
			.willReturn(Mono.just(10L));

		StepVerifier.create(this.followService.getFollowersCount(this.userId)).expectNext(10L).verifyComplete();
	}

	@Test
	void getFollowingCount_Success() {
		given(this.countCache.get(anyString(), any())).willAnswer(invocation -> invocation.getArgument(1));
		given(this.followRepository.countByFollowerIdAndStatus(any(UUID.class), eq("ACCEPTED")))
			.willReturn(Mono.just(5L));

		StepVerifier.create(this.followService.getFollowingCount(this.userId)).expectNext(5L).verifyComplete();
	}

	@Test
	void getProfileInteractions_Success_CombinesCountsAndInteractions() {
		// The combined read zips the existing getFollowCounts read (here served from the count
		// cache) with the existing tweet-interactions read, and groups them into one DTO with the
		// per-tweet entries in request order.
		UUID tweetId = UUID.randomUUID();
		ReplyDto topReply = new ReplyDto().setId(UUID.randomUUID()).setContent("top");
		// getFollowCounts eagerly builds the cache-miss loader from countFollowersAndFollowing
		// before getPair is subscribed; stub it so that construction does not NPE even though the
		// stubbed getPair serves the counts and the loader is never subscribed.
		given(this.followRepository.countFollowersAndFollowing(any(UUID.class), eq(Status.ACCEPTED.name())))
			.willReturn(Mono.just(new ro.tweebyte.interactionservice.repository.FollowCountsRow(20L, 10L)));
		given(this.countCache.getPair(anyString(), anyString(), any()))
			.willReturn(Mono.just(new long[] { 20L, 10L }));
		given(this.tweetInteractionsService.getTweetInteractionsEntries(List.of(tweetId)))
			.willReturn(Mono.just(List.of(new TweetInteractionsEntryDto(tweetId, 3L, 2L, 1L, topReply))));

		StepVerifier.create(this.followService.getProfileInteractions(this.userId, List.of(tweetId)))
			.assertNext(dto -> {
				org.assertj.core.api.Assertions.assertThat(dto.getFollowCounts().getFollowers()).isEqualTo(20L);
				org.assertj.core.api.Assertions.assertThat(dto.getFollowCounts().getFollowing()).isEqualTo(10L);
				org.assertj.core.api.Assertions.assertThat(dto.getTweetInteractions()).hasSize(1);
				var entry = dto.getTweetInteractions().get(0);
				org.assertj.core.api.Assertions.assertThat(entry.tweetId()).isEqualTo(tweetId);
				org.assertj.core.api.Assertions.assertThat(entry.likes()).isEqualTo(3L);
				org.assertj.core.api.Assertions.assertThat(entry.replies()).isEqualTo(2L);
				org.assertj.core.api.Assertions.assertThat(entry.retweets()).isEqualTo(1L);
				org.assertj.core.api.Assertions.assertThat(entry.topReply()).isEqualTo(topReply);
			})
			.verifyComplete();

		verify(this.tweetInteractionsService).getTweetInteractionsEntries(List.of(tweetId));
	}

	@Test
	void getProfileInteractions_EmptyTweetIds_RealCountsNoInteractionsCall() {
		// An empty tweet page must still read the real follow counts but skip the interactions
		// sub-call entirely, yielding an empty tweet-interactions list.
		given(this.followRepository.countFollowersAndFollowing(any(UUID.class), eq(Status.ACCEPTED.name())))
			.willReturn(Mono.just(new ro.tweebyte.interactionservice.repository.FollowCountsRow(7L, 4L)));
		given(this.countCache.getPair(anyString(), anyString(), any()))
			.willReturn(Mono.just(new long[] { 7L, 4L }));

		StepVerifier.create(this.followService.getProfileInteractions(this.userId, List.of()))
			.assertNext(dto -> {
				org.assertj.core.api.Assertions.assertThat(dto.getFollowCounts().getFollowers()).isEqualTo(7L);
				org.assertj.core.api.Assertions.assertThat(dto.getFollowCounts().getFollowing()).isEqualTo(4L);
				org.assertj.core.api.Assertions.assertThat(dto.getTweetInteractions()).isEmpty();
			})
			.verifyComplete();

		verify(this.tweetInteractionsService, never()).getTweetInteractionsEntries(any());
	}

	@Test
	void getFollowers_Success() {
		FollowEntity follower = new FollowEntity();
		follower.setFollowerId(UUID.randomUUID());
		follower.setFollowedId(this.userId);
		follower.setStatus("ACCEPTED");

		UserDto followerSummary = new UserDto();
		followerSummary.setId(follower.getFollowerId());
		followerSummary.setUserName(this.userName);

		FollowDto expectedDto = new FollowDto();
		expectedDto.setFollowerId(follower.getFollowerId());
		expectedDto.setFollowedId(follower.getFollowedId());
		expectedDto.setStatus(Status.ACCEPTED);

		given(this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(any(UUID.class), eq("ACCEPTED")))
			.willReturn(Flux.just(follower));
		given(this.userService.getUserSummary(follower.getFollowerId())).willReturn(Mono.just(followerSummary));
		given(this.followMapper.mapEntityToDto(follower, this.userName)).willReturn(expectedDto);

		StepVerifier.create(this.followService.getFollowers(this.userId)).expectNext(expectedDto).verifyComplete();
	}

	@Test
	void follow_Success() {
		UserDto followedSummary = new UserDto();
		followedSummary.setId(this.followedId);
		followedSummary.setIsPrivate(false);
		followedSummary.setUserName("testUser");

		FollowEntity savedFollow = new FollowEntity();
		savedFollow.setId(UUID.randomUUID());
		savedFollow.setFollowerId(this.userId);
		savedFollow.setFollowedId(this.followedId);
		savedFollow.setStatus("ACCEPTED");

		given(this.userService.fetchUserSummary(this.followedId)).willReturn(Mono.just(followedSummary));
		given(this.followMapper.mapRequestToEntity(any(UUID.class), any(UUID.class), anyString()))
			.willReturn(savedFollow);
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(Mono.just(savedFollow));
		// An accepted follow increments following + followers counters in one batched round-trip.
		given(this.countCache.incrementBoth(anyString(), anyString())).willReturn(Mono.empty());
		// follow() evicts the actor's following + recommendations caches (two keys).
		given(this.redisTemplate.delete(anyString(), anyString())).willReturn(Mono.just(2L));

		// Following a public user returns an empty completion since the controller
		// responds with no content, so verify completion with no next signal.
		StepVerifier.create(this.followService.follow(this.userId, this.followedId)).verifyComplete();
	}

	@Test
	void unfollow_Success() {
		// unfollow() resolves the edge first; an ACCEPTED edge drives the conditional decrement.
		FollowEntity edge = new FollowEntity();
		edge.setStatus(Status.ACCEPTED.name());
		given(this.followRepository.findByFollowerIdAndFollowedId(any(UUID.class), any(UUID.class)))
			.willReturn(Mono.just(edge));
		given(this.followRepository.deleteByFollowerIdAndFollowedId(any(UUID.class), any(UUID.class)))
			.willReturn(Mono.empty());
		given(this.countCache.decrementBoth(anyString(), anyString())).willReturn(Mono.empty());
		// unfollow() evicts the actor's following + recommendations caches (two keys).
		given(this.redisTemplate.delete(anyString(), anyString())).willReturn(Mono.just(2L));

		StepVerifier.create(this.followService.unfollow(this.userId, this.followedId)).verifyComplete();

		verify(this.redisTemplate).delete(anyString(), anyString());
	}

	@Test
	void updateFollowRequest_Success() {
		UUID followRequestId = UUID.randomUUID();
		UUID followerId = this.userId;

		FollowEntity request = new FollowEntity();
		request.setId(followRequestId);
		request.setFollowerId(UUID.randomUUID());
		request.setFollowedId(followerId);
		request.setStatus(Status.PENDING.name());

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.just(request));
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(Mono.just(request));
		// Accepting a pending request increments following + followers counters in one batched round-trip.
		given(this.countCache.incrementBoth(anyString(), anyString())).willReturn(Mono.empty());
		given(this.redisTemplate.delete(anyString())).willReturn(Mono.just(1L));

		StepVerifier.create(this.followService.updateFollowRequest(followerId, followRequestId, Status.ACCEPTED))
			.verifyComplete();

		verify(this.followRepository).findById(followRequestId);
		verify(this.followRepository).save(any(FollowEntity.class));
		verify(this.redisTemplate).delete(anyString());
	}

	@Test
	void getFollowedIdentifiers_Success() {
		UUID expectedFollowedId = UUID.randomUUID();
		FollowEntity acceptedFollow = new FollowEntity();
		acceptedFollow.setFollowedId(expectedFollowedId);

		given(this.followRepository.findByFollowerIdAndStatus(any(UUID.class), eq("ACCEPTED")))
			.willReturn(Flux.just(acceptedFollow));

		StepVerifier.create(this.followService.getFollowedIdentifiers(this.userId))
			.expectNext(expectedFollowedId)
			.verifyComplete();
	}

	@Test
	void getFollowing_Success_CacheHit() {
		// cache hit returns the
		// bytes directly without hitting the repository.
		given(this.valueOperations.get(anyString())).willReturn(Mono.just(new byte[] { 1, 2, 3 }));

		StepVerifier.create(this.followService.getFollowing(this.userId))
			.expectNextMatches(bytes -> bytes.length == 3 && bytes[0] == 1 && bytes[1] == 2 && bytes[2] == 3)
			.verifyComplete();

		verify(this.valueOperations).get(anyString());
	}

	@Test
	void getFollowRequests_Success() {
		// Pending requests
		// for a user are mapped via FollowMapper.mapEntityToDto.
		FollowEntity pending = new FollowEntity();
		pending.setStatus("PENDING");
		FollowDto pendingDto = new FollowDto();
		pendingDto.setStatus(Status.PENDING);

		given(this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(any(UUID.class), eq("PENDING")))
			.willReturn(Flux.just(pending));
		given(this.followMapper.mapEntityToDto(pending)).willReturn(pendingDto);

		StepVerifier.create(this.followService.getFollowRequests(this.userId)).expectNext(pendingDto).verifyComplete();

		verify(this.followRepository).findByFollowedIdAndStatusOrderByCreatedAtDesc(this.userId, "PENDING");
		verify(this.followMapper).mapEntityToDto(pending);
	}

	@Test
	void updateFollowRequest_NotFound_ShouldError() {
		// Updating an unknown follow request must surface a not-found error signal
		// rather than completing silently.
		UUID followRequestId = UUID.randomUUID();
		UUID requestingUserId = UUID.randomUUID();

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.empty());

		StepVerifier.create(this.followService.updateFollowRequest(requestingUserId, followRequestId, Status.ACCEPTED))
			.expectError(ro.tweebyte.interactionservice.exception.FollowNotFoundException.class)
			.verify();

		verify(this.followRepository).findById(followRequestId);
		verify(this.followRepository, never()).save(any(FollowEntity.class));
		// Eviction is composed via then(), so it must not run on the error path.
		verify(this.redisTemplate, never()).delete(anyString());
	}

}
