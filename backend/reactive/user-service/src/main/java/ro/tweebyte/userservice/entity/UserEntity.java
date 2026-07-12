/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table("users")
@Builder
public class UserEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column("user_name")
	private String userName;

	@Column("email")
	private String email;

	@Column("biography")
	private String biography;

	@Column("password")
	private String password;

	@Column("birth_date")
	private LocalDate birthDate;

	@Column("created_at")
	private LocalDateTime createdAt;

	@Column("is_private")
	private Boolean isPrivate;

	// Optional avatar → media_assets.id (nullable). get-profile surfaces just this
	// id; the client fetches the image via file-download. A plain scalar, so the
	// profile read never joins media_assets.
	@Column("profile_picture_id")
	private UUID profilePictureId;

	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
