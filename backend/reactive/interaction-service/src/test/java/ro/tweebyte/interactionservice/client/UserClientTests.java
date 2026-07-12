/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserClientTests {

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

	private UserClient userClient;

	@BeforeEach
	void setUp() {
		this.userClient = new UserClient(this.webClientBuilder);
		given(this.webClientBuilder.baseUrl(any())).willReturn(this.webClientBuilder);
		given(this.webClientBuilder.build()).willReturn(this.webClient);
		this.userClient.init();
		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
	}

	@Test
	void getUserSummary_Success() {
		UUID userId = UUID.randomUUID();
		UserDto expectedUser = new UserDto();
		expectedUser.setId(userId);

		given(this.responseSpec.bodyToMono(UserDto.class)).willReturn(Mono.just(expectedUser));

		StepVerifier.create(this.userClient.getUserSummary(userId)).expectNext(expectedUser).verifyComplete();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
		verify(this.responseSpec).bodyToMono(UserDto.class);
	}

	@Test
	void getUserSummary_UserNotFound() {
		UUID userId = UUID.randomUUID();
		WebClientResponseException notFoundException = WebClientResponseException.create(HttpStatus.NOT_FOUND.value(),
				"Not Found", null, null, null);

		given(this.responseSpec.bodyToMono(UserDto.class)).willReturn(Mono.error(notFoundException));

		StepVerifier.create(this.userClient.getUserSummary(userId))
			.expectErrorMatches(throwable -> throwable instanceof UserNotFoundException
					&& throwable.getMessage().equals("User not found with id: " + userId))
			.verify();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
		verify(this.responseSpec).bodyToMono(UserDto.class);
	}

	@Test
	void getUserSummary_OtherError() {
		UUID userId = UUID.randomUUID();
		WebClientResponseException internalServerError = WebClientResponseException
			.create(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", null, null, null);

		given(this.responseSpec.bodyToMono(UserDto.class)).willReturn(Mono.error(internalServerError));

		StepVerifier.create(this.userClient.getUserSummary(userId))
			.expectErrorMatches(throwable -> throwable instanceof InteractionException
					&& throwable.getCause() == internalServerError)
			.verify();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
		verify(this.responseSpec).bodyToMono(UserDto.class);
	}

}
