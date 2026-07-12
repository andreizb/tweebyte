/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceAdditionalTests {

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaAssetRepository mediaAssetRepository;

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private TweetClient tweetClient;

	@Mock
	private UserMapper userMapper;

	@Mock
	private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;

	@Mock
	private UserUpdateRetryDelegate userUpdateRetryDelegate;

	@Mock
	private org.springframework.beans.factory.ObjectProvider<ro.tweebyte.userservice.client.KeycloakClient> keycloakClient;

	@Mock
	private ExecutorService executorService;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setup() {
		ReflectionTestUtils.setField(this.userService, "executorService", Executors.newFixedThreadPool(1));
	}

	@Test
	void getUserSummaryThrowsWhenUserNotFound() {
		UUID userId = UUID.randomUUID();
		given(this.userRepository.findById(userId)).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.userService.getUserSummary(userId).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getUserSummaryByUserNameThrowsWhenUserNotFound() {
		given(this.userRepository.findByUserName("missing")).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.userService.getUserSummaryByUserName("missing").join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void updateUserThrowsWhenEmailAlreadyExists() {
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("dup@example.com");

		// FIX 3: pre-check now uses existsByEmailAndIdNot so the user's own row is excluded.
		given(this.userRepository.existsByEmailAndIdNot("dup@example.com", userId)).willReturn(true);

		Throwable ex = catchThrowable(() -> this.userService.updateUser(userId, req).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserAlreadyExistsException.class);
		verify(this.userUpdateRetryDelegate, never()).save(any());
	}

	@Test
	void updateUserThrowsWhenUserNameAlreadyExists() {
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setUserName("takenname");

		// FIX 3: pre-check now uses existsByUserNameAndIdNot so the user's own row is excluded.
		given(this.userRepository.existsByUserNameAndIdNot("takenname", userId)).willReturn(true);

		Throwable ex = catchThrowable(() -> this.userService.updateUser(userId, req).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserAlreadyExistsException.class);
		verify(this.userUpdateRetryDelegate, never()).save(any());
	}

	@Test
	void updateUserThrowsWhenUserNotFound() {
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		// both null -> skip duplicate checks, proceed to findById which returns empty

		given(this.userRepository.findById(userId)).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.userService.updateUser(userId, req).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
		verify(this.userUpdateRetryDelegate, never()).save(any());
	}

	@Test
	void updateUserWithEmailAndNameAndExistingUser() {
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("e@e.com");
		req.setUserName("n");
		UserEntity entity = new UserEntity();

		// FIX 3: pre-checks now exclude the user's own row via existsByEmailAndIdNot / existsByUserNameAndIdNot.
		given(this.userRepository.existsByEmailAndIdNot("e@e.com", userId)).willReturn(false);
		given(this.userRepository.existsByUserNameAndIdNot("n", userId)).willReturn(false);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(entity));

		this.userService.updateUser(userId, req).join();

		verify(this.userMapper).mapRequestToEntity(req, entity);
		verify(this.userUpdateRetryDelegate).save(entity);
	}

	// --- Keycloak credential propagation (B1) -------------------------

	@Test
	void updateUserPropagatesEmailAndPasswordToKeycloakWhenBeanPresent() {
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("new@example.com");
		req.setPassword("rawPass");
		UserEntity entity = new UserEntity();
		ro.tweebyte.userservice.client.KeycloakClient client = org.mockito.Mockito
			.mock(ro.tweebyte.userservice.client.KeycloakClient.class);

		// FIX 3: uses existsByEmailAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByEmailAndIdNot("new@example.com", userId)).willReturn(false);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(entity));
		given(this.passwordEncoder.encode("rawPass")).willReturn("hashed");
		given(this.keycloakClient.getIfAvailable()).willReturn(client);

		this.userService.updateUser(userId, req).join();

		// raw password (not the in-place hashed value) reaches Keycloak.
		verify(client).updateCredentials(userId.toString(), "new@example.com", "rawPass");
	}

	@Test
	void updateUserSkipsKeycloakWhenBeanAbsent() {
		// Benchmark profile: KeycloakClient bean absent → no propagation, measured path unchanged.
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("new@example.com");
		UserEntity entity = new UserEntity();

		// FIX 3: uses existsByEmailAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByEmailAndIdNot("new@example.com", userId)).willReturn(false);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(entity));
		given(this.keycloakClient.getIfAvailable()).willReturn(null);

		this.userService.updateUser(userId, req).join();

		verify(this.keycloakClient).getIfAvailable();
		verify(this.userUpdateRetryDelegate).save(entity);
	}

	@Test
	void updateUserSkipsKeycloakWhenNoEmailOrPasswordChanged() {
		// Neither email nor password supplied → propagation short-circuits before touching the
		// provider (getIfAvailable never called), even with the bean present.
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setUserName("newname");
		UserEntity entity = new UserEntity();

		// FIX 3: uses existsByUserNameAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByUserNameAndIdNot("newname", userId)).willReturn(false);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(entity));

		this.userService.updateUser(userId, req).join();

		verify(this.keycloakClient, never()).getIfAvailable();
		verify(this.userUpdateRetryDelegate).save(entity);
	}

}
