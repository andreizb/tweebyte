/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTests {

	private static final String TOKEN = "realm-issued-access-token";

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaAssetRepository mediaAssetRepository;

	@Mock
	private UserMapper userMapper;

	@Mock
	private BCryptPasswordEncoder passwordEncoder;

	@Mock
	private KeycloakClient keycloakClient;

	@InjectMocks
	private AuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.authenticationService, "executorService", Executors.newFixedThreadPool(1));
	}

	@Test
	void testLoginSuccess() throws ExecutionException, InterruptedException {
		// Keycloak is the credential authority: login is a pure pass-through of the
		// realm-issued token.
		given(this.keycloakClient.issueToken(anyString(), anyString())).willReturn(TOKEN);
		UserLoginRequest request = new UserLoginRequest("user@example.com", "correctpassword");

		AuthenticationResponse result = this.authenticationService.login(request).get();

		assertThat(result.getToken()).isEqualTo(TOKEN);
		verify(this.keycloakClient).issueToken("user@example.com", "correctpassword");
	}

	@Test
	void testLoginFailure() {
		// A wrong email/password is a 401 from the token endpoint, surfaced by
		// KeycloakClient as AuthenticationException.
		given(this.keycloakClient.issueToken(anyString(), anyString()))
			.willThrow(new AuthenticationException("Invalid username or password"));
		UserLoginRequest request = new UserLoginRequest("user@example.com", "wrongpassword");

		assertThatThrownBy(() -> this.authenticationService.login(request).get())
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(AuthenticationException.class);
	}

	@Test
	void testRegisterSuccess() throws ExecutionException, InterruptedException {
		// register pre-checks existsByEmail / existsByUserName, saves the local profile row,
		// provisions the credential in Keycloak, then issues a token.
		UserRegisterRequest request = new UserRegisterRequest();
		request.setEmail("newuser@example.com");
		request.setUserName("user");
		request.setPassword("newpassword");

		UserEntity userEntity = new UserEntity();
		userEntity.setEmail("newuser@example.com");
		userEntity.setPassword("$2a$10$SomeHashedPasswordHere");
		userEntity.setId(UUID.randomUUID());

		given(this.userRepository.existsByEmail(anyString())).willReturn(false);
		given(this.userRepository.existsByUserName(anyString())).willReturn(false);
		given(this.userMapper.mapRequestToEntity(request)).willReturn(userEntity);
		given(this.userRepository.save(any(UserEntity.class))).willReturn(userEntity);
		given(this.keycloakClient.issueToken(anyString(), anyString())).willReturn(TOKEN);

		AuthenticationResponse result = this.authenticationService.register(request).get();

		assertThat(result.getToken()).isEqualTo(TOKEN);
		verify(this.userRepository).save(any(UserEntity.class));
		verify(this.userMapper).mapRequestToEntity(request);
		verify(this.keycloakClient).provisionUser("newuser@example.com", "newpassword",
				userEntity.getId().toString());
	}

}
