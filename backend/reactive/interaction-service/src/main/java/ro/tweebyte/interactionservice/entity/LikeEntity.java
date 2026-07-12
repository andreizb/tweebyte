/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

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

@Table("likes")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class LikeEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column("created_at")
	private LocalDateTime createdAt;

	@Transient
	private boolean isInsertable;

	@Column("user_id")
	private UUID userId;

	@Column("likeable_id")
	private UUID likeableId;

	@Column("likeable_type")
	private String likeableType;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
