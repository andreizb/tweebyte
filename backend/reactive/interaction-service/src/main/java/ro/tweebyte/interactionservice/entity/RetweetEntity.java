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

@Table("retweets")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RetweetEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column("created_at")
	private LocalDateTime createdAt;

	@Column("original_tweet_id")
	private UUID originalTweetId;

	@Column("retweeter_id")
	private UUID retweeterId;

	@Column("content")
	private String content;

	@Column("media_ids")
	private UUID[] mediaIds;

	@Transient
	private boolean isInsertable;

	@Override
	public boolean isNew() {
		return this.isInsertable || this.id == null;
	}

}
