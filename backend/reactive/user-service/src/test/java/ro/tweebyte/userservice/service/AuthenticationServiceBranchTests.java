/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercises every branch in AuthenticationService now that Keycloak owns issuance: - login:
 * pass-through of the realm token, IdP rejection → AuthenticationException - register:
 * emailTaken / userNameTaken short-circuits, profile-picture validation, save-failure
 * propagation, and the save → Keycloak provision → token-issue happy path (the local
 * profile UUID is handed to Keycloak as the user_id attribute).
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceBranchTests {

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

	private UserEntity userEntity;

	private final UUID userId = UUID.randomUUID();

	private final String userEmail = "user@example.com";

	private final String userPassword = "$2a$10$hashedPassword";

	@BeforeEach
	void setUp() {
		this.userEntity = new UserEntity();
		this.userEntity.setId(this.userId);
		this.userEntity.setEmail(this.userEmail);
		this.userEntity.setPassword(this.userPassword);
		this.userEntity.setUserName("alice");
	}

	// --- login ----------------------------------------------------------

	@Test
	void loginPassesThroughRealmToken() {
		given(this.keycloakClient.issueToken(this.userEmail, "plain")).willReturn(Mono.just(TOKEN));

		StepVerifier.create(this.authenticationService.login(new UserLoginRequest(this.userEmail, "plain")))
			.assertNext(response -> assertThat(response.getToken()).isEqualTo(TOKEN))
			.verifyComplete();
	}

	@Test
	void loginIdpRejectionEmitsAuthenticationException() {
		given(this.keycloakClient.issueToken(anyString(), anyString()))
			.willReturn(Mono.error(new AuthenticationException("Invalid username or password")));

		StepVerifier.create(this.authenticationService.login(new UserLoginRequest("nobody@example.com", "x")))
			.expectErrorMatches(
					t -> t instanceof AuthenticationException && t.getMessage().equals("Invalid username or password"))
			.verify();
	}

	// --- register -------------------------------------------------------

	@Test
	void registerEmailAlreadyTakenEmitsUserAlreadyExists() {
		given(this.userRepository.existsByEmail(this.userEmail)).willReturn(Mono.just(true));

		UserRegisterRequest req = registerRequest("alice");

		StepVerifier.create(this.authenticationService.register(req))
			.expectErrorMatches(t -> t instanceof UserAlreadyExistsException
					&& t.getMessage().equals("A user with this email already exists"))
			.verify();

		verify(this.userRepository, never()).existsByUserName(anyString());
		verify(this.userRepository, never()).save(any());
		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
	}

	@Test
	void registerUserNameAlreadyTakenEmitsUserAlreadyExists() {
		given(this.userRepository.existsByEmail(this.userEmail)).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName("alice")).willReturn(Mono.just(true));

		UserRegisterRequest req = registerRequest("alice");

		StepVerifier.create(this.authenticationService.register(req))
			.expectErrorMatches(t -> t instanceof UserAlreadyExistsException
					&& t.getMessage().equals("A user with this username already exists"))
			.verify();

		verify(this.userRepository, never()).save(any());
		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
	}

	@Test
	void registerHappyPathProvisionsWithLocalIdThenIssuesToken() {
		given(this.userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
		given(this.userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(this.userEntity);
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		given(this.keycloakClient.provisionUser(anyString(), anyString(), anyString())).willReturn(Mono.empty());
		given(this.keycloakClient.issueToken(anyString(), anyString())).willReturn(Mono.just(TOKEN));

		UserRegisterRequest req = registerRequest("alice");

		StepVerifier.create(this.authenticationService.register(req))
			.assertNext(response -> assertThat(response.getToken()).isEqualTo(TOKEN))
			.verifyComplete();

		ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.keycloakClient).provisionUser(emailCaptor.capture(), passwordCaptor.capture(),
				userIdCaptor.capture());
		assertThat(emailCaptor.getValue()).isEqualTo(this.userEmail);
		assertThat(passwordCaptor.getValue()).isEqualTo("password1");
		assertThat(userIdCaptor.getValue()).isEqualTo(this.userId.toString());
	}

	@Test
	void registerSaveFailurePropagatesAndSkipsProvisioning() {
		given(this.userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
		given(this.userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(this.userEntity);
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.error(new RuntimeException("db down")));

		UserRegisterRequest req = registerRequest("alice");

		StepVerifier.create(this.authenticationService.register(req))
			.expectErrorMatches(t -> t instanceof RuntimeException && "db down".equals(t.getMessage()))
			.verify();

		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
	}

	@Test
	void registerProvisioningFailurePropagatesAndSkipsIssue() {
		// FIX 1: on provisioning failure the local row must be deleted (compensation)
		// before the error is rethrown. Token issuance is skipped.
		given(this.userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
		given(this.userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(this.userEntity);
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		given(this.keycloakClient.provisionUser(anyString(), anyString(), anyString())).willReturn(
				Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider rejected user")));
		// Compensation: deleteById must complete so the error can be rethrown.
		given(this.userRepository.deleteById(eq(this.userId))).willReturn(Mono.empty());

		UserRegisterRequest req = registerRequest("alice");

		StepVerifier.create(this.authenticationService.register(req))
			.expectError(ResponseStatusException.class)
			.verify();

		verify(this.keycloakClient, never()).issueToken(anyString(), anyString());
		verify(this.userRepository).deleteById(eq(this.userId));
	}

	@Test
	void registerProfilePicturePresentAndExistsProceeds() {
		UUID pictureId = UUID.randomUUID();
		given(this.userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
		given(this.userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(this.userEntity);
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(Mono.just(true));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		given(this.keycloakClient.provisionUser(anyString(), anyString(), anyString())).willReturn(Mono.empty());
		given(this.keycloakClient.issueToken(anyString(), anyString())).willReturn(Mono.just(TOKEN));

		UserRegisterRequest req = registerRequest("alice");
		req.setProfilePictureId(pictureId);

		StepVerifier.create(this.authenticationService.register(req))
			.assertNext(response -> assertThat(response.getToken()).isEqualTo(TOKEN))
			.verifyComplete();
	}

	@Test
	void registerProfilePictureMissingEmitsBadRequest() {
		UUID pictureId = UUID.randomUUID();
		given(this.userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
		given(this.userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(this.userEntity);
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(Mono.just(false));
		// save is evaluated eagerly as the .then(...) argument even though the missing
		// picture short-circuits before it is subscribed.
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));

		UserRegisterRequest req = registerRequest("alice");
		req.setProfilePictureId(pictureId);

		StepVerifier.create(this.authenticationService.register(req))
			.expectError(ResponseStatusException.class)
			.verify();

		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
		verify(this.keycloakClient, never()).issueToken(anyString(), anyString());
	}

	private UserRegisterRequest registerRequest(String userName) {
		UserRegisterRequest req = new UserRegisterRequest();
		req.setEmail(this.userEmail);
		req.setUserName(userName);
		req.setPassword("password1");
		return req;
	}

}
