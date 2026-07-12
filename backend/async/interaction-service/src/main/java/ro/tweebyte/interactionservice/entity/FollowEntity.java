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
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "follows",
		uniqueConstraints = { @UniqueConstraint(name = "uq_follows_follower_followed",
				columnNames = { "follower_id", "followed_id" }) },
		indexes = { @Index(name = "follows_followed_status_idx", columnList = "followed_id, status"),
				@Index(name = "follows_follower_status_idx", columnList = "follower_id, status") })
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class FollowEntity extends InteractionEntity implements Persistable<UUID> {

	@Column(name = "follower_id", nullable = false)
	private UUID followerId;

	@Column(name = "followed_id", nullable = false)
	private UUID followedId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status;

	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || getId() == null;
	}

	public enum Status {

		/** A follow request awaiting the followed user's approval. */
		PENDING,
		/** An active follow relationship. */
		ACCEPTED,
		/** A follow request that was declined. */
		REJECTED

	}

}
