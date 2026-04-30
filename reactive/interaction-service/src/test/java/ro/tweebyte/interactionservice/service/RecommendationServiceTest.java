package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

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
		ReflectionTestUtils.setField(recommendationService, "self", recommendationService);
		valueOperations = mock(ReactiveValueOperations.class);
		// Lenient: not every test exercises the redis cache path (e.g. the
		// fetchPopularHashtags delegate test only touches TweetService).
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Test
	void recommendUsersToFollow_Success() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();

		ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		UserDto userDto = new UserDto();
		userDto.setId(userId);
		String cachedData = objectMapper.writeValueAsString(List.of(userDto));
		when(valueOperations.get(anyString())).thenReturn(Mono.just(cachedData));

		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowedId(UUID.randomUUID());
		when(followRepository.findByFollowerIdAndStatus(any(UUID.class), eq(Status.ACCEPTED.name())))
			.thenReturn(Flux.just(followEntity));

		StepVerifier.create(recommendationService.recommendUsersToFollow(userId))
			.expectNextMatches(user -> user.getId().equals(userId))
			.verifyComplete();

		verify(followRepository).findByFollowerIdAndStatus(any(UUID.class), eq(Status.ACCEPTED.name()));
	}

	@Test
	void getUserRecommendations_FromCache_Success() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();
		UserDto userDto = new UserDto();
		userDto.setId(userId);
		String key = "follow_recommendations:" + userId;
		String cachedData = objectMapper.writeValueAsString(List.of(userDto));

		when(followRepository.findByFollowerIdAndStatus(any(UUID.class), eq(Status.ACCEPTED.name())))
			.thenReturn(Flux.just(new FollowEntity()));

		when(valueOperations.get(key)).thenReturn(Mono.just(cachedData));

		StepVerifier.create(recommendationService.getUserRecommendations(userId))
			.expectNext(userDto)
			.verifyComplete();

		verify(valueOperations).get(key);
	}

	@Test
	void fetchPopularUsers_Success() throws JsonProcessingException {
		String cachedData = objectMapper.writeValueAsString(Map.of(UUID.randomUUID(), 1.0));

		when(valueOperations.get("popular_users:")).thenReturn(Mono.just(cachedData));
		when(followRepository.findAllFollowedIds())
			.thenReturn(Flux.just(UUID.randomUUID()));

		// fetchPopularUsers returns Mono<Map<UUID,Double>> directly so the
		// caller composes it via flatMapMany without blocking.
		StepVerifier.create(recommendationService.fetchPopularUsers())
			.expectNextMatches(result -> {
				assertNotNull(result);
				assertFalse(result.isEmpty());
				return true;
			})
			.verifyComplete();

		verify(valueOperations).get("popular_users:");
	}

	@Test
	void computePopularUsers_EmptyFollowedIds_ProducesEmptyMap() throws JsonProcessingException {
		// When the
		// followed-ids stream is empty, fetchPopularUsers (cache-miss path) must
		// short-circuit to an empty map and never call tweet/like/retweet services.
		// fetchPopularUsers returns Mono; we drive it via a cache miss so the
		// internal computePopularUsers() is exercised.
		when(valueOperations.get("popular_users:")).thenReturn(Mono.empty());
		when(valueOperations.set(eq("popular_users:"), anyString())).thenReturn(Mono.just(true));
		when(followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		StepVerifier.create(recommendationService.fetchPopularUsers())
			.expectNextMatches(Map::isEmpty)
			.verifyComplete();

		verify(followRepository).findAllFollowedIds();
		verify(tweetService, times(0)).getUserTweetsSummary(any());
		verify(likeService, times(0)).getTweetLikesCount(any());
	}

	@Test
	void fetchPopularHashtags_DelegatesToTweetService() {
		// The
		// reactive RecommendationService.fetchPopularHashtags simply delegates to
		// TweetService.getPopularHashtags.
		TweetDto.HashtagDto hashtag = new TweetDto.HashtagDto();
		when(tweetService.getPopularHashtags()).thenReturn(Flux.just(hashtag));

		StepVerifier.create(recommendationService.fetchPopularHashtags())
			.expectNext(hashtag)
			.verifyComplete();

		verify(tweetService).getPopularHashtags();
	}

	@Test
	void fetchPopularUsers_CacheMiss_ComputesAndStores() throws JsonProcessingException {
		// fetchPopularUsers cache-miss path:
		// when the popular_users key is empty, computePopularUsers runs and the
		// resulting map is written back to Redis.
		// still asserts the final return type is Mono<Map<UUID,Double>>.
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(tweetId);

		when(valueOperations.get("popular_users:")).thenReturn(Mono.empty());
		when(valueOperations.set(eq("popular_users:"), anyString())).thenReturn(Mono.just(true));
		when(followRepository.findAllFollowedIds()).thenReturn(Flux.just(userId));
		when(tweetService.getUserTweetsSummary(userId)).thenReturn(Flux.just(tweetDto));
		when(followRepository.countByFollowedIdAndStatus(eq(userId), eq(Status.ACCEPTED.name())))
			.thenReturn(Mono.just(10L));
		when(likeService.getTweetLikesCount(tweetId)).thenReturn(Mono.just(5L));
		when(retweetService.getRetweetCountOfTweet(tweetId)).thenReturn(Mono.just(2L));

		StepVerifier.create(recommendationService.fetchPopularUsers())
			.expectNextMatches(result -> {
				assertNotNull(result);
				assertEquals(1, result.size());
				assertTrue(result.get(userId) > 0);
				return true;
			})
			.verifyComplete();

		verify(followRepository).findAllFollowedIds();
		verify(valueOperations).set(eq("popular_users:"), anyString());
	}

	@Test
	void computePopularUsersAndScore_AggregatesAcrossUsers() throws JsonProcessingException {
		// computePopularUsers + score:
		// two users with different follower / like / retweet counts both yield
		// strictly-positive scores in the resulting map.
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();
		UUID tweetId1 = UUID.randomUUID();
		UUID tweetId2 = UUID.randomUUID();
		TweetDto t1 = new TweetDto();
		t1.setId(tweetId1);
		TweetDto t2 = new TweetDto();
		t2.setId(tweetId2);

		when(valueOperations.get("popular_users:")).thenReturn(Mono.empty());
		when(valueOperations.set(eq("popular_users:"), anyString())).thenReturn(Mono.just(true));
		when(followRepository.findAllFollowedIds()).thenReturn(Flux.just(userId1, userId2));
		when(tweetService.getUserTweetsSummary(userId1)).thenReturn(Flux.just(t1));
		when(tweetService.getUserTweetsSummary(userId2)).thenReturn(Flux.just(t2));
		when(followRepository.countByFollowedIdAndStatus(eq(userId1), eq(Status.ACCEPTED.name())))
			.thenReturn(Mono.just(10L));
		when(followRepository.countByFollowedIdAndStatus(eq(userId2), eq(Status.ACCEPTED.name())))
			.thenReturn(Mono.just(20L));
		when(likeService.getTweetLikesCount(tweetId1)).thenReturn(Mono.just(5L));
		when(likeService.getTweetLikesCount(tweetId2)).thenReturn(Mono.just(15L));
		when(retweetService.getRetweetCountOfTweet(tweetId1)).thenReturn(Mono.just(2L));
		when(retweetService.getRetweetCountOfTweet(tweetId2)).thenReturn(Mono.just(4L));

		StepVerifier.create(recommendationService.fetchPopularUsers())
			.expectNextMatches(result -> {
				assertEquals(2, result.size());
				assertTrue(result.get(userId1) > 0);
				assertTrue(result.get(userId2) > 0);
				return true;
			})
			.verifyComplete();

		verify(followRepository).findAllFollowedIds();
		verify(tweetService, times(2)).getUserTweetsSummary(any());
	}

}
