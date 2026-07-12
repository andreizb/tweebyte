/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.keycloak", name = "enabled", havingValue = "true")
public class AuthenticationService {

	private final UserRepository userRepository;

	private final MediaAssetRepository mediaAssetRepository;

	private final UserMapper userMapper;

	private final BCryptPasswordEncoder passwordEncoder;

	private final KeycloakClient keycloakClient;

	@Qualifier("ioExecutor")
	private final ExecutorService executorService;

	public CompletableFuture<AuthenticationResponse> login(UserLoginRequest request) {
		// Keycloak is the credential authority: login is a pure pass-through of the
		// realm-issued token. An unknown email or wrong password yields a 401 from the
		// token endpoint, which KeycloakClient maps to AuthenticationException (→ 401).
		return CompletableFuture
			.supplyAsync(() -> this.keycloakClient.issueToken(request.getEmail(), request.getPassword()),
					this.executorService)
			.thenApply(token -> new AuthenticationResponse().setToken(token));
	}

	@RateLimiter(name = "userServiceRateLimiter")
	public CompletableFuture<AuthenticationResponse> register(UserRegisterRequest request) {
		return CompletableFuture.supplyAsync(() -> {
			// pre-check unique constraints and surface a structured 400 via
			// UserAlreadyExistsException; otherwise the DB layer's unique-violation leaks
			// as a raw 500. Matches reactive's behaviour.
			if (this.userRepository.existsByEmail(request.getEmail())) {
				throw new UserAlreadyExistsException("A user with this email already exists");
			}
			if (this.userRepository.existsByUserName(request.getUserName())) {
				throw new UserAlreadyExistsException("A user with this username already exists");
			}

			// When a picture is supplied it must reference a real asset; absent, the
			// mapper defaults to the seeded sentinel (no lookup needed).
			if (request.getProfilePictureId() != null
					&& !this.mediaAssetRepository.existsById(request.getProfilePictureId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Profile picture not found for id: " + request.getProfilePictureId());
			}

			// The local row stays the profile authority; its bcrypt password is vestigial
			// now that Keycloak holds the credential, but the column is NOT NULL. Hash here,
			// ahead of the persist: the mapper is a pure transform and never sees the encoder.
			UserEntity userEntity = this.userMapper.mapRequestToEntity(request);
			userEntity.setPassword(this.passwordEncoder.encode(request.getPassword()));
			UserEntity saved;
			try {
				saved = this.userRepository.save(userEntity);
			}
			catch (DataIntegrityViolationException ex) {
				// Concurrent insert raced past the pre-checks and hit the DB unique constraint.
				// Translate to the same structured 400 the pre-checks produce so the client
				// receives UserAlreadyExistsException → 400 instead of a raw 500.
				throw new UserAlreadyExistsException("A user with this email or username already exists");
			}

			// id is present post-save and becomes the Keycloak user_id attribute, so the
			// realm-issued token carries the same owner id the gateway enforces on. Provision
			// before issuing — a provisioning failure compensates by deleting the local row
			// (orphan prevention) then rethrows so the token is never issued.
			UUID id = Objects.requireNonNull(saved.getId(), "Persisted user must have an id");
			try {
				this.keycloakClient.provisionUser(saved.getEmail(), request.getPassword(), id.toString());
			}
			catch (Exception ex) {
				// Roll back the local row so the email is not permanently taken.
				this.userRepository.deleteById(id);
				throw ex;
			}
			return new AuthenticationResponse()
				.setToken(this.keycloakClient.issueToken(saved.getEmail(), request.getPassword()));
		}, this.executorService);
	}

}
