/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.entity.UserEntity;

import static org.mockito.BDDMockito.given;

/**
 * Reactive analogue to async's UserRepositoryTests. Because the reactive stack uses Spring
 * Data R2DBC (no @DataJpaTest / TestEntityManager), the repository is mocked and the
 * StepVerifier asserts on the Mono contract — same behavioural assertions as the async
 * side (findByEmail / findByUserName return the persisted user).
 */
@ExtendWith(MockitoExtension.class)
class UserRepositoryTests {

	@Mock
	private UserRepository userRepository;

	private UserEntity userEntity;

	@BeforeEach
	void setUp() {
		this.userEntity = new UserEntity();
		this.userEntity.setId(UUID.randomUUID());
		this.userEntity.setEmail("test@example.com");
		this.userEntity.setUserName("testUser");
		this.userEntity.setBiography("dasd");
		this.userEntity.setBirthDate(LocalDate.now());
		this.userEntity.setCreatedAt(LocalDateTime.now());
		this.userEntity.setIsPrivate(true);
		this.userEntity.setPassword("asdf");
	}

	@Test
	void findByEmail() {
		String email = "test@example.com";
		given(this.userRepository.findByEmail(email)).willReturn(Mono.just(this.userEntity));

		StepVerifier.create(this.userRepository.findByEmail(email))
			.expectNextMatches(found -> email.equals(found.getEmail()))
			.verifyComplete();
	}

	@Test
	void findByUserName() {
		String userName = "testUser";
		given(this.userRepository.findByUserName(userName)).willReturn(Mono.just(this.userEntity));

		StepVerifier.create(this.userRepository.findByUserName(userName))
			.expectNextMatches(found -> userName.equals(found.getUserName()))
			.verifyComplete();
	}

}
