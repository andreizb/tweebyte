package ro.tweebyte.interactionservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserClientTest {

	@Mock
	private WebClient.Builder webClientBuilder;

	@Mock
	private WebClient webClient;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpec;

	@Mock
	private WebClient.ResponseSpec responseSpec;

	@InjectMocks
	private UserClient userClient;

	@BeforeEach
	void setUp() {
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
	}

	@Test
	void getUserSummary_Success() {
		UUID userId = UUID.randomUUID();
		UserDto expectedUser = new UserDto();
		expectedUser.setId(userId);

		when(responseSpec.bodyToMono(UserDto.class)).thenReturn(Mono.just(expectedUser));

		StepVerifier.create(userClient.getUserSummary(userId))
			.expectNext(expectedUser)
			.verifyComplete();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
		verify(responseSpec).bodyToMono(UserDto.class);
	}

	@Test
	void getUserSummary_UserNotFound() {
		UUID userId = UUID.randomUUID();
		WebClientResponseException notFoundException = WebClientResponseException.create(
			HttpStatus.NOT_FOUND.value(),
			"Not Found",
			null,
			null,
			null
		);

		when(responseSpec.bodyToMono(UserDto.class)).thenReturn(Mono.error(notFoundException));

		StepVerifier.create(userClient.getUserSummary(userId))
			.expectErrorMatches(throwable -> throwable instanceof UserNotFoundException &&
				throwable.getMessage().equals("User not found with id: " + userId))
			.verify();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
		verify(responseSpec).bodyToMono(UserDto.class);
	}

	@Test
	void getUserSummary_OtherError() {
		UUID userId = UUID.randomUUID();
		WebClientResponseException internalServerError = WebClientResponseException.create(
			HttpStatus.INTERNAL_SERVER_ERROR.value(),
			"Internal Server Error",
			null,
			null,
			null
		);

		when(responseSpec.bodyToMono(UserDto.class)).thenReturn(Mono.error(internalServerError));

		StepVerifier.create(userClient.getUserSummary(userId))
			.expectErrorMatches(throwable -> throwable instanceof InteractionException &&
				throwable.getCause() == internalServerError)
			.verify();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
		verify(responseSpec).bodyToMono(UserDto.class);
	}
}