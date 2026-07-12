/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTests {

	private static final String TOKEN = "realm-issued-access-token";

	@InjectMocks
	private AuthenticationService authenticationService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaAssetRepository mediaAssetRepository;

	@Mock
	private BCryptPasswordEncoder passwordEncoder;

	@Mock
	private UserMapper userMapper;

	@Mock
	private KeycloakClient keycloakClient;

	private final String userEmail = "test@example.com";

	private final String userPassword = "password";

	private final UserEntity userEntity = new UserEntity();

	@BeforeEach
	void setUp() {
		this.userEntity.setId(UUID.randomUUID());
		this.userEntity.setEmail(this.userEmail);
		this.userEntity.setPassword(this.userPassword);
	}

	@Test
	void loginSuccess() {
		// Keycloak is the credential authority: login is a pure pass-through of the
		// realm-issued token.
		given(this.keycloakClient.issueToken(anyString(), anyString())).willReturn(Mono.just(TOKEN));
		UserLoginRequest loginRequest = new UserLoginRequest(this.userEmail, this.userPassword);

		StepVerifier.create(this.authenticationService.login(loginRequest))
			.expectNextMatches(response -> TOKEN.equals(response.getToken()))
			.verifyComplete();
	}

	@Test
	void loginFailure() {
		// A wrong email/password is a 401 from the token endpoint, surfaced by
		// KeycloakClient as AuthenticationException.
		given(this.keycloakClient.issueToken(anyString(), anyString()))
			.willReturn(Mono.error(new AuthenticationException("Invalid username or password")));
		UserLoginRequest loginRequest = new UserLoginRequest(this.userEmail, "wrongPassword");

		StepVerifier.create(this.authenticationService.login(loginRequest))
			.expectError(AuthenticationException.class)
			.verify();
	}

	@Test
	void registerSuccess() {
		// register pre-checks existsByEmail / existsByUserName, saves the local profile row,
		// provisions the credential in Keycloak, then issues a token.
		given(this.userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		given(this.userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(this.userEntity);
		given(this.keycloakClient.provisionUser(anyString(), anyString(), anyString())).willReturn(Mono.empty());
		given(this.keycloakClient.issueToken(anyString(), anyString())).willReturn(Mono.just(TOKEN));

		UserRegisterRequest registerRequest = new UserRegisterRequest();
		registerRequest.setUserName("user");
		registerRequest.setEmail(this.userEmail);
		registerRequest.setPassword(this.userPassword);

		StepVerifier.create(this.authenticationService.register(registerRequest))
			.expectNextMatches(response -> TOKEN.equals(response.getToken()))
			.verifyComplete();
	}

}
