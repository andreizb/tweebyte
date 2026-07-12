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
@Table(name = "retweets",
		indexes = { @Index(name = "retweets_original_tweet_idx", columnList = "original_tweet_id"),
				@Index(name = "retweets_retweeter_idx", columnList = "retweeter_id") })
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RetweetEntity extends InteractionEntity {

	@Column(name = "original_tweet_id", nullable = false)
	private UUID originalTweetId;

	@Column(name = "retweeter_id", nullable = false)
	private UUID retweeterId;

	@Column(name = "content")
	private String content;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "media_ids")
	private UUID[] mediaIds;

}
