/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.tweebyte.userservice.entity.UserEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class UserRepositoryTests {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	@Test
	void findByEmail() {
		String email = "test@example.com";
		UserEntity userEntity = new UserEntity();
		userEntity.setId(UUID.randomUUID());
		userEntity.setEmail(email);
		userEntity.setBiography("dasd");
		userEntity.setBirthDate(LocalDate.now());
		userEntity.setCreatedAt(LocalDateTime.now());
		userEntity.setEmail(email);
		userEntity.setIsPrivate(true);
		userEntity.setUserName("test");
		userEntity.setPassword("asdf");

		this.entityManager.persist(userEntity);
		this.entityManager.flush();

		Optional<UserEntity> foundUser = this.userRepository.findByEmail(email);

		assertThat(foundUser).isPresent();
		assertThat(foundUser.get().getEmail()).isEqualTo(email);
	}

	@Test
	void findByUserName() {
		String email = "test@example.com";
		String userName = "testUser";
		UserEntity userEntity = new UserEntity();
		userEntity.setId(UUID.randomUUID());
		userEntity.setUserName(userName);
		userEntity.setBiography("dasd");
		userEntity.setBirthDate(LocalDate.now());
		userEntity.setCreatedAt(LocalDateTime.now());
		userEntity.setEmail(email);
		userEntity.setIsPrivate(true);
		userEntity.setPassword("asdf");

		this.entityManager.persist(userEntity);
		this.entityManager.flush();

		Optional<UserEntity> foundUser = this.userRepository.findByUserName(userName);

		assertThat(foundUser).isPresent();
		assertThat(foundUser.get().getUserName()).isEqualTo(userName);
	}

	@Test
	void save_registerShapedEntity_insertsDirectly() {
		// Mirrors the register path: PK assigned client-side + isInsertable=true so
		// JpaRepository.save() routes through Persistable.isNew()==true → persist()
		// (direct INSERT, no SELECT-before-INSERT probe). Guards the F16 fix: an
		// @GeneratedValue id would make persist() reject the non-null-id entity as
		// detached, so an assigned-id strategy is required for this to INSERT cleanly.
		UUID id = UUID.randomUUID();
		UserEntity userEntity = new UserEntity();
		userEntity.setId(id);
		userEntity.setInsertable(true);
		userEntity.setUserName("registerUser");
		userEntity.setEmail("register@example.com");
		userEntity.setBiography("bio");
		userEntity.setBirthDate(LocalDate.now());
		userEntity.setCreatedAt(LocalDateTime.now());
		userEntity.setIsPrivate(false);
		userEntity.setPassword("pw");

		UserEntity saved = this.userRepository.save(userEntity);
		this.entityManager.flush();

		assertThat(saved.getId()).isEqualTo(id);
		assertThat(this.userRepository.findById(id)).isPresent();
	}

}
