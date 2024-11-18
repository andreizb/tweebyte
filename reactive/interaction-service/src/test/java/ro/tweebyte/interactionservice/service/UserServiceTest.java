package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

	@Mock
	private UserClient userClient;

	@Mock
	private ReactiveRedisTemplate<String, Object> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, Object> valueOperations;

	@InjectMocks
	private UserService userService;

	private final UUID userId = UUID.randomUUID();
	private final String redisKey = "users:" + userId;

	private UserDto mockUser;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockUser = new UserDto();
		mockUser.setId(userId);
		mockUser.setUserName("testuser");
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Test
	void getUserSummary_cacheMiss() {
		when(valueOperations.get(redisKey)).thenReturn(Mono.empty());
		when(userClient.getUserSummary(userId)).thenReturn(Mono.just(mockUser));
		when(valueOperations.set(redisKey, mockUser)).thenReturn(Mono.empty());

		Mono<UserDto> result = userService.getUserSummary(userId);

		StepVerifier.create(result)
			.expectNext(mockUser)
			.verifyComplete();

		verify(valueOperations).get(redisKey);
		verify(userClient).getUserSummary(userId);
		verify(valueOperations).set(redisKey, mockUser);
	}

	@Test
	void getUserSummary_cacheMiss_clientError() {
		when(valueOperations.get(redisKey)).thenReturn(Mono.empty());
		when(userClient.getUserSummary(userId)).thenReturn(Mono.error(new RuntimeException("Client error")));

		Mono<UserDto> result = userService.getUserSummary(userId);

		StepVerifier.create(result)
			.expectError(RuntimeException.class)
			.verify();

		verify(valueOperations).get(redisKey);
		verify(userClient).getUserSummary(userId);
		verify(valueOperations, never()).set(anyString(), any());
	}
}
