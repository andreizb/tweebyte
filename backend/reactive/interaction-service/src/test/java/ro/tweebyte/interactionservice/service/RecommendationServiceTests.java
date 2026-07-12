/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTests {

	@InjectMocks
	private RecommendationService recommendationService;

	@Mock
	private UserService userService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private ReactiveRedisTemplate<String, String> redisTemplate;

	private ReactiveValueOperations<String, String> valueOperations;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private TweetService tweetService;

	@Mock
	private LikeService likeService;

	@Mock
	private RetweetService retweetService;

	@BeforeEach
	void setup() {
		ReflectionTestUtils.setField(this.recommendationService, "self", this.recommendationService);
		// @Value-injected TTL is null without a Spring context; set it so the
		// cache-miss set(...) writes pass a real Duration (matches production).
		ReflectionTestUtils.setField(this.recommendationService, "cacheTtl", Duration.ofSeconds(60));
		this.valueOperations = mock(ReactiveValueOperations.class);
		// Lenient: not every test exercises the redis cache path (e.g. the
		// fetchPopularHashtags delegate test only touches TweetService).
		lenient().when(this.redisTemplate.opsForValue()).thenReturn(this.valueOperations);
	}

	@Test
	void recommendUsersToFollow_Success() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();

		ReactiveValueOperations<String, String> localValueOperations = mock(ReactiveValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(localValueOperations);

		UserDto userDto = new UserDto();
		userDto.setId(userId);
		String cachedData = this.objectMapper.writeValueAsString(List.of(userDto));
		given(localValueOperations.get(anyString())).willReturn(Mono.just(cachedData));

		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowedId(UUID.randomUUID());
		given(this.followRepository.findByFollowerIdAndStatus(any(UUID.class), eq(Status.ACCEPTED.name())))
			.willReturn(Flux.just(followEntity));

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId))
			.expectNextMatches(user -> user.getId().equals(userId))
			.verifyComplete();

		verify(this.followRepository).findByFollowerIdAndStatus(any(UUID.class), eq(Status.ACCEPTED.name()));
	}

	@Test
	void getUserRecommendations_FromCache_Success() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();
		UserDto userDto = new UserDto();
		userDto.setId(userId);
		String key = "follow_recommendations::" + userId;
		String cachedData = this.objectMapper.writeValueAsString(List.of(userDto));

		given(this.followRepository.findByFollowerIdAndStatus(any(UUID.class), eq(Status.ACCEPTED.name())))
			.willReturn(Flux.just(new FollowEntity()));

		given(this.valueOperations.get(key)).willReturn(Mono.just(cachedData));

		StepVerifier.create(this.recommendationService.getUserRecommendations(userId))
			.expectNext(userDto)
			.verifyComplete();

		verify(this.valueOperations).get(key);
	}

	@Test
	void fetchPopularUsers_Success() throws JsonProcessingException {
		String cachedData = this.objectMapper.writeValueAsString(Map.of(UUID.randomUUID(), 1.0));

		given(this.valueOperations.get("popular_users::")).willReturn(Mono.just(cachedData));
		given(this.followRepository.findAllFollowedIds()).willReturn(Flux.just(UUID.randomUUID()));

		// fetchPopularUsers returns Mono<Map<UUID,Double>> directly so the
		// caller composes it via flatMapMany without blocking.
		StepVerifier.create(this.recommendationService.fetchPopularUsers()).expectNextMatches(result -> {
			assertThat(result).isNotNull();
			assertThat(result).isNotEmpty();
			return true;
		}).verifyComplete();

		verify(this.valueOperations).get("popular_users::");
	}

	@Test
	void computePopularUsers_EmptyFollowedIds_ProducesEmptyMap() {
		// When the
		// followed-ids stream is empty, fetchPopularUsers (cache-miss path) must
		// short-circuit to an empty map and never call tweet/like/retweet services.
		// fetchPopularUsers returns Mono; we drive it via a cache miss so the
		// internal computePopularUsers() is exercised.
		given(this.valueOperations.get("popular_users::")).willReturn(Mono.empty());
		given(this.valueOperations.set(eq("popular_users::"), anyString(), any(Duration.class)))
			.willReturn(Mono.just(true));
		given(this.followRepository.findAllFollowedIds()).willReturn(Flux.empty());

		StepVerifier.create(this.recommendationService.fetchPopularUsers())
			.expectNextMatches(Map::isEmpty)
			.verifyComplete();

		verify(this.followRepository).findAllFollowedIds();
		verify(this.tweetService, times(0)).getUserTweetsSummary(any());
		verify(this.likeService, times(0)).getTweetLikesCounts(any());
	}

	@Test
	void fetchPopularHashtags_DelegatesToTweetService() {
		// The
		// reactive RecommendationService.fetchPopularHashtags simply delegates to
		// TweetService.getPopularHashtags.
		TweetDto.HashtagDto hashtag = new TweetDto.HashtagDto();
		given(this.tweetService.getPopularHashtags()).willReturn(Flux.just(hashtag));

		StepVerifier.create(this.recommendationService.fetchPopularHashtags()).expectNext(hashtag).verifyComplete();

		verify(this.tweetService).getPopularHashtags();
	}

	@Test
	void fetchPopularUsers_CacheMiss_ComputesAndStores() {
		// fetchPopularUsers cache-miss path:
		// when the popular_users key is empty, computePopularUsers runs and the
		// resulting map is written back to Redis.
		// still asserts the final return type is Mono<Map<UUID,Double>>.
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetSummaryDto tweetSummary = new TweetSummaryDto();
		tweetSummary.setId(tweetId);

		given(this.valueOperations.get("popular_users::")).willReturn(Mono.empty());
		given(this.valueOperations.set(eq("popular_users::"), anyString(), any(Duration.class)))
			.willReturn(Mono.just(true));
		given(this.followRepository.findAllFollowedIds()).willReturn(Flux.just(userId));
		given(this.tweetService.getUserTweetsSummary(userId)).willReturn(Flux.just(tweetSummary));
		given(this.followRepository.countByFollowedIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Mono.just(10L));
		given(this.likeService.getTweetLikesCounts(anyList())).willReturn(Mono.just(Map.of(tweetId, 5L)));
		given(this.retweetService.getRetweetCountsForTweets(anyList())).willReturn(Mono.just(Map.of(tweetId, 2L)));

		StepVerifier.create(this.recommendationService.fetchPopularUsers()).expectNextMatches(result -> {
			assertThat(result).isNotNull();
			assertThat(result).hasSize(1);
			assertThat(result.get(userId)).isGreaterThan(0.0);
			return true;
		}).verifyComplete();

		verify(this.followRepository).findAllFollowedIds();
		verify(this.valueOperations).set(eq("popular_users::"), anyString(), any(Duration.class));
	}

	@Test
	void fetchPopularUsers_SingleFlightInitializedByPostConstruct_Reused() {
		// PostConstruct wires the shared single-flight Mono before any request; the
		// cache-miss path then reuses that instance rather than re-initializing it.
		given(this.valueOperations.get("popular_users::")).willReturn(Mono.empty());
		given(this.valueOperations.set(eq("popular_users::"), anyString(), any(Duration.class)))
			.willReturn(Mono.just(true));
		given(this.followRepository.findAllFollowedIds()).willReturn(Flux.empty());

		// Drive the PostConstruct initializer so the single-flight field is non-null
		// before fetchPopularUsers subscribes.
		this.recommendationService.initPopularUsersSingleFlight();

		StepVerifier.create(this.recommendationService.fetchPopularUsers())
			.expectNextMatches(Map::isEmpty)
			.verifyComplete();
	}

	@Test
	void computePopularUsersAndScore_AggregatesAcrossUsers() {
		// computePopularUsers + score:
		// two users with different follower / like / retweet counts both yield
		// strictly-positive scores in the resulting map.
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();
		UUID tweetId1 = UUID.randomUUID();
		UUID tweetId2 = UUID.randomUUID();
		TweetSummaryDto t1 = new TweetSummaryDto();
		t1.setId(tweetId1);
		TweetSummaryDto t2 = new TweetSummaryDto();
		t2.setId(tweetId2);

		given(this.valueOperations.get("popular_users::")).willReturn(Mono.empty());
		given(this.valueOperations.set(eq("popular_users::"), anyString(), any(Duration.class)))
			.willReturn(Mono.just(true));
		given(this.followRepository.findAllFollowedIds()).willReturn(Flux.just(userId1, userId2));
		given(this.tweetService.getUserTweetsSummary(userId1)).willReturn(Flux.just(t1));
		given(this.tweetService.getUserTweetsSummary(userId2)).willReturn(Flux.just(t2));
		given(this.followRepository.countByFollowedIdAndStatus(userId1, Status.ACCEPTED.name()))
			.willReturn(Mono.just(10L));
		given(this.followRepository.countByFollowedIdAndStatus(userId2, Status.ACCEPTED.name()))
			.willReturn(Mono.just(20L));
		given(this.likeService.getTweetLikesCounts(anyList())).willReturn(Mono.just(Map.of(tweetId1, 5L)))
			.willReturn(Mono.just(Map.of(tweetId2, 15L)));
		given(this.retweetService.getRetweetCountsForTweets(anyList())).willReturn(Mono.just(Map.of(tweetId1, 2L)))
			.willReturn(Mono.just(Map.of(tweetId2, 4L)));

		StepVerifier.create(this.recommendationService.fetchPopularUsers()).expectNextMatches(result -> {
			assertThat(result).hasSize(2);
			assertThat(result.get(userId1)).isGreaterThan(0.0);
			assertThat(result.get(userId2)).isGreaterThan(0.0);
			return true;
		}).verifyComplete();

		verify(this.followRepository).findAllFollowedIds();
		verify(this.tweetService, times(2)).getUserTweetsSummary(any());
	}

}
