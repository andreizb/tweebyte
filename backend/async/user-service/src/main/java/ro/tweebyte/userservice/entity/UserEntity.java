/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class UserEntity implements Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_name", nullable = false, unique = true)
	private String userName;

	@Column(name = "email", nullable = false, unique = true)
	private String email;

	@Column(name = "biography", nullable = false)
	private String biography;

	@Column(name = "password", nullable = false)
	private String password;

	@Column(name = "is_private", nullable = false)
	private Boolean isPrivate;

	@Column(name = "birth_date", nullable = false)
	private LocalDate birthDate;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	// Optional avatar → media_assets.id (nullable). get-profile surfaces just this
	// id; the client fetches the image via file-download. No association mapping —
	// a plain scalar so the profile query never joins media_assets.
	@Column(name = "profile_picture_id")
	private UUID profilePictureId;

	// Register assigns the PK client-side, so JpaRepository.save() would otherwise route
	// through merge() and issue a SELECT-before-INSERT probe. Persistable.isNew() returns
	// true when this flag is set (the register mapper sets it), steering save() straight to
	// persist() — a direct INSERT, no probe. Entities loaded via findById leave the flag at
	// its default false, so the update path still merges (UPDATE). Transient: not a column.
	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
