/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.repository;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.entity.UserEntity;

@Repository
public interface UserRepository extends ReactiveCrudRepository<UserEntity, UUID> {

	Mono<UserEntity> findByEmail(String email);

	Mono<UserEntity> findByUserName(String userName);

	// register pre-check needs these to mirror async's existsByEmail / existsByUserName.
	Mono<Boolean> existsByEmail(String email);

	Mono<Boolean> existsByEmailAndIdNot(String email, UUID id);

	Mono<Boolean> existsByUserName(String userName);

	Mono<Boolean> existsByUserNameAndIdNot(String userName, UUID id);

	// ILIKE (case-insensitive) — matches async's UserRepository.
	@Query("SELECT * FROM users WHERE user_name ILIKE :query ORDER BY id LIMIT :limit OFFSET :offset")
	Flux<UserEntity> searchUsers(String query, int limit, int offset);

	// Local media references: the avatars users currently point at. The
	// stale-media GC keeps these regardless of age (a referenced row is never
	// orphaned). profile_picture_id → media_assets(id) is an in-DB FK.
	@Query("SELECT DISTINCT profile_picture_id FROM users WHERE profile_picture_id IS NOT NULL")
	Flux<UUID> findReferencedProfilePictureIds();

}
