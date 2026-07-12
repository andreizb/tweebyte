/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "likes",
		uniqueConstraints = { @UniqueConstraint(name = "uq_likes_user_likeable",
				columnNames = { "user_id", "likeable_id", "likeable_type" }) },
		indexes = { @Index(name = "likes_likeable_idx", columnList = "likeable_id, likeable_type") })
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class LikeEntity extends InteractionEntity {

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "likeable_id", nullable = false)
	private UUID likeableId;

	@Enumerated(EnumType.STRING)
	@Column(name = "likeable_type", nullable = false)
	private LikeableType likeableType;

	public enum LikeableType {

		/** The like targets a tweet. */
		TWEET,
		/** The like targets a reply. */
		REPLY

	}

}
