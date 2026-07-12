/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.userservice.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, UUID id);

	boolean existsByUserName(String userName);

	boolean existsByUserNameAndIdNot(String userName, UUID id);

	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByUserName(String userName);

	@Query(value = "SELECT * FROM users WHERE user_name ILIKE :query ORDER BY id LIMIT :limit OFFSET :offset",
			nativeQuery = true)
	List<UserEntity> searchUsers(@Param("query") String query, @Param("limit") int limit, @Param("offset") int offset);

	// Local media references: the avatars users currently point at. The
	// stale-media GC keeps these regardless of age (a referenced row is never
	// orphaned). profile_picture_id → media_assets(id) is an in-DB FK.
	@Query(value = "SELECT DISTINCT profile_picture_id FROM users WHERE profile_picture_id IS NOT NULL",
			nativeQuery = true)
	List<UUID> findReferencedProfilePictureIds();

}
