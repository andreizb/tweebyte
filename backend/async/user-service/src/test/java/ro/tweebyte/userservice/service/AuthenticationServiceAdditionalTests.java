/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceAdditionalTests {

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
	void loginWithWrongCredentialsThrowsAuthenticationException() {
		given(this.keycloakClient.issueToken("user@example.com", "wrong"))
			.willThrow(new AuthenticationException("Invalid username or password"));
		UserLoginRequest request = new UserLoginRequest("user@example.com", "wrong");

		Throwable ex = catchThrowable(() -> this.authenticationService.login(request).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(AuthenticationException.class);
	}

	@Test
	void registerThrowsWhenEmailAlreadyExists() {
		UserRegisterRequest request = new UserRegisterRequest();
		request.setEmail("existing@example.com");
		request.setUserName("name");
		request.setPassword("pwd");

		given(this.userRepository.existsByEmail("existing@example.com")).willReturn(true);

		Throwable ex = catchThrowable(() -> this.authenticationService.register(request).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserAlreadyExistsException.class);
		verify(this.userRepository, never()).save(any());
		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
	}

	@Test
	void registerThrowsWhenUserNameAlreadyExists() {
		UserRegisterRequest request = new UserRegisterRequest();
		request.setEmail("new@example.com");
		request.setUserName("takenname");
		request.setPassword("pwd");

		given(this.userRepository.existsByEmail("new@example.com")).willReturn(false);
		given(this.userRepository.existsByUserName("takenname")).willReturn(true);

		Throwable ex = catchThrowable(() -> this.authenticationService.register(request).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserAlreadyExistsException.class);
		verify(this.userRepository, never()).save(any());
		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
	}

	@Test
	void registerProvisioningFailurePropagatesAndSkipsIssue() {
		// FIX 1: on provisioning failure the local row must be deleted (compensation)
		// before the error is rethrown. Token issuance is skipped.
		UserRegisterRequest request = new UserRegisterRequest();
		request.setEmail("new@example.com");
		request.setUserName("name");
		request.setPassword("pwd");

		UserEntity userEntity = new UserEntity();
		userEntity.setEmail("new@example.com");
		userEntity.setId(UUID.randomUUID());

		given(this.userRepository.existsByEmail("new@example.com")).willReturn(false);
		given(this.userRepository.existsByUserName("name")).willReturn(false);
		given(this.userMapper.mapRequestToEntity(request)).willReturn(userEntity);
		given(this.userRepository.save(any(UserEntity.class))).willReturn(userEntity);
		willThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider rejected user"))
			.given(this.keycloakClient)
			.provisionUser(anyString(), anyString(), anyString());

		Throwable ex = catchThrowable(() -> this.authenticationService.register(request).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		verify(this.keycloakClient, never()).issueToken(anyString(), anyString());
		// Compensation: the orphan local row is deleted.
		verify(this.userRepository).deleteById(eq(userEntity.getId()));
	}

	@Test
	void registerWithMissingProfilePictureThrowsBadRequest() {
		// profilePictureId present but not a real asset → the non-null + not-exists branch
		// of the picture guard fires a structured 400 before any persist/provision.
		UUID pictureId = UUID.randomUUID();
		UserRegisterRequest request = new UserRegisterRequest();
		request.setEmail("new@example.com");
		request.setUserName("name");
		request.setPassword("pwd");
		request.setProfilePictureId(pictureId);

		given(this.userRepository.existsByEmail("new@example.com")).willReturn(false);
		given(this.userRepository.existsByUserName("name")).willReturn(false);
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(false);

		Throwable ex = catchThrowable(() -> this.authenticationService.register(request).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		assertThat(((ResponseStatusException) ex.getCause()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(this.userRepository, never()).save(any());
		verify(this.keycloakClient, never()).provisionUser(anyString(), anyString(), anyString());
	}

	@Test
	void registerWithExistingProfilePictureProvisionsAndIssuesToken() throws Exception {
		// profilePictureId present AND a real asset → the guard passes, the local row is
		// saved, Keycloak provisions with the profile UUID, then a token is issued.
		UUID pictureId = UUID.randomUUID();
		UserRegisterRequest request = new UserRegisterRequest();
		request.setEmail("new@example.com");
		request.setUserName("name");
		request.setPassword("pwd");
		request.setProfilePictureId(pictureId);

		UserEntity userEntity = new UserEntity();
		userEntity.setEmail("new@example.com");
		userEntity.setId(UUID.randomUUID());

		given(this.userRepository.existsByEmail("new@example.com")).willReturn(false);
		given(this.userRepository.existsByUserName("name")).willReturn(false);
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(true);
		given(this.userMapper.mapRequestToEntity(request)).willReturn(userEntity);
		given(this.passwordEncoder.encode("pwd")).willReturn("hashed");
		given(this.userRepository.save(any(UserEntity.class))).willReturn(userEntity);
		given(this.keycloakClient.issueToken("new@example.com", "pwd")).willReturn("realm-token");

		AuthenticationResponse result = this.authenticationService.register(request).get();
		assertThat(result.getToken()).isEqualTo("realm-token");
		verify(this.keycloakClient).provisionUser("new@example.com", "pwd", userEntity.getId().toString());
	}

}
