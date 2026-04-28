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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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

		StepVerifier.create(Mono.just(recommendationService.fetchPopularUsers()))
			.expectNextMatches(result -> {
				assertNotNull(result);
				assertFalse(result.isEmpty());
				return true;
			})
			.verifyComplete();

		verify(valueOperations).get("popular_users:");
	}

}
