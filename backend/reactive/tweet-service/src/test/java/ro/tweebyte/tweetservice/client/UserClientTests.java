/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserClientTests {

	@InjectMocks
	private UserClient userClient;

	@Mock
	private WebClient.Builder webClientBuilderMock;

	@Mock
	private WebClient webClientMock;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpecMock;

	@Mock
	private WebClient.ResponseSpec responseSpecMock;

	@BeforeEach
	void setUp() {
		given(this.webClientBuilderMock.baseUrl(any())).willReturn(this.webClientBuilderMock);
		given(this.webClientBuilderMock.build()).willReturn(this.webClientMock);
		this.userClient.init();
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(String.class), any(Object[].class)))
			.willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.onStatus(any(), any())).willReturn(this.responseSpecMock);
	}

	@Test
	void testGetUserSummaryByName_success() {
		String userName = "testUser";
		UserDto expectedUser = new UserDto();
		expectedUser.setUserName(userName);

		given(this.responseSpecMock.bodyToMono(UserDto.class)).willReturn(Mono.just(expectedUser));

		StepVerifier.create(this.userClient.getUserSummary(userName)).expectNext(expectedUser).verifyComplete();

		verify(this.webClientMock).get();
		verify(this.requestHeadersUriSpecMock).uri("/users/summary/name/{userName}", userName);
		verify(this.requestHeadersSpecMock).retrieve();
		verify(this.responseSpecMock).bodyToMono(UserDto.class);
	}

	@Test
	void testGetUserSummaryById_success() {
		UUID userId = UUID.randomUUID();
		UserDto expectedUser = new UserDto();
		expectedUser.setId(userId);

		given(this.responseSpecMock.bodyToMono(UserDto.class)).willReturn(Mono.just(expectedUser));

		StepVerifier.create(this.userClient.getUserSummary(userId)).expectNext(expectedUser).verifyComplete();

		verify(this.webClientMock).get();
		verify(this.requestHeadersUriSpecMock).uri("/users/summary/{userId}", userId);
		verify(this.requestHeadersSpecMock).retrieve();
		verify(this.responseSpecMock).bodyToMono(UserDto.class);
	}

	@Test
	void testGetUserSummaryByName_userNotFound() {
		String userName = "nonexistentUser";

		given(this.responseSpecMock.bodyToMono(UserDto.class))
			.willReturn(Mono.error(new UserNotFoundException("User not found for name: " + userName)));

		StepVerifier.create(this.userClient.getUserSummary(userName))
			.expectErrorMatches(throwable -> throwable instanceof UserNotFoundException
					&& throwable.getMessage().equals("User not found for name: " + userName))
			.verify();

		verify(this.webClientMock).get();
		verify(this.requestHeadersUriSpecMock).uri("/users/summary/name/{userName}", userName);
		verify(this.requestHeadersSpecMock).retrieve();
	}

	@Test
	void testGetUserSummaryById_userNotFound() {
		UUID userId = UUID.randomUUID();

		given(this.responseSpecMock.bodyToMono(UserDto.class))
			.willReturn(Mono.error(new UserNotFoundException("User not found for id: " + userId)));

		StepVerifier.create(this.userClient.getUserSummary(userId))
			.expectErrorMatches(throwable -> throwable instanceof UserNotFoundException
					&& throwable.getMessage().equals("User not found for id: " + userId))
			.verify();

		verify(this.webClientMock).get();
		verify(this.requestHeadersUriSpecMock).uri("/users/summary/{userId}", userId);
		verify(this.requestHeadersSpecMock).retrieve();
	}

}
