/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "replies", indexes = { @Index(name = "replies_tweet_idx", columnList = "tweet_id, created_at") })
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ReplyEntity extends InteractionEntity {

	@Column(name = "tweet_id", nullable = false)
	private UUID tweetId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "content")
	private String content;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "media_ids")
	private UUID[] mediaIds;

}
