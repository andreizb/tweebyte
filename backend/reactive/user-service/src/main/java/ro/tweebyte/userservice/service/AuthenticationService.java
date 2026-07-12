/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.Objects;
import java.util.UUID;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

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

	private final BCryptPasswordEncoder passwordEncoder;

	private final UserMapper userMapper;

	private final KeycloakClient keycloakClient;

	public Mono<AuthenticationResponse> login(UserLoginRequest request) {
		// Keycloak is the credential authority: an unknown email or wrong password yields a
		// 401 from the token endpoint, which KeycloakClient maps to AuthenticationException
		// (→ 401) — same shape as the prior self-signed login path.
		return this.keycloakClient.issueToken(request.getEmail(), request.getPassword())
			.map(token -> new AuthenticationResponse().setToken(token));
	}

	@RateLimiter(name = "userServiceRateLimiter")
	public Mono<AuthenticationResponse> register(UserRegisterRequest request) {
		// pre-check unique constraints and surface a structured 400 via
		// UserAlreadyExistsException → GlobalExceptionHandler. Without this, the DB
		// layer's DataIntegrityViolation leaks to the client as a raw 500 with the
		// postgres `duplicate key value violates unique constraint "uk..."` message.
		// Matches async's behaviour.
		return this.userRepository.existsByEmail(request.getEmail()).flatMap(emailTaken -> {
			if (Boolean.TRUE.equals(emailTaken)) {
				return Mono.error(new UserAlreadyExistsException("A user with this email already exists"));
			}
			return this.userRepository.existsByUserName(request.getUserName());
		}).flatMap(usernameTaken -> {
			if (Boolean.TRUE.equals(usernameTaken)) {
				return Mono.error(new UserAlreadyExistsException("A user with this username already exists"));
			}
			// When a picture is supplied it must reference a real asset; absent, the
			// mapper defaults to the seeded sentinel (no lookup needed). The local row
			// stays the profile authority; its bcrypt password is vestigial now that
			// Keycloak holds the credential, but the column is NOT NULL.
			UserEntity userEntity = this.userMapper.mapRequestToEntity(request);
			userEntity.setPassword(this.passwordEncoder.encode(request.getPassword()));
			return ensureProfilePictureExists(request.getProfilePictureId())
				.then(this.userRepository.save(userEntity)
					.onErrorMap(DataIntegrityViolationException.class,
							ex -> new UserAlreadyExistsException(
									"A user with this email or username already exists")));
		}).flatMap(saved -> {
			// UserEntity is a Persistable whose id is nullable until persisted; this runs
			// post-save, so the id is present and becomes the Keycloak user_id attribute.
			UUID id = Objects.requireNonNull(saved.getId(), "Persisted user must have an id");
			// Provision then issue. On provisioning failure compensate by deleting the
			// just-saved local row (orphan prevention) then rethrow so the token is never
			// issued. The happy path is 100% unchanged.
			return this.keycloakClient.provisionUser(saved.getEmail(), request.getPassword(), id.toString())
				.onErrorResume(ex -> this.userRepository.deleteById(id).then(Mono.error(ex)))
				.then(Mono.defer(() -> this.keycloakClient.issueToken(saved.getEmail(), request.getPassword())))
				.map(token -> new AuthenticationResponse().setToken(token));
		});
	}

	private Mono<Void> ensureProfilePictureExists(UUID profilePictureId) {
		if (profilePictureId == null) {
			return Mono.empty();
		}
		return this.mediaAssetRepository.existsById(profilePictureId)
			.flatMap(exists -> Boolean.TRUE.equals(exists) ? Mono.empty()
					: Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Profile picture not found for id: " + profilePictureId)));
	}

}
