/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("tweets")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TweetEntity {

	@Id
	private UUID id;

	@Column("user_id")
	private UUID userId;

	@Version
	private Long version;

	@Column("content")
	private String content;

	@Column("created_at")
	private LocalDateTime createdAt;

	@Column("media_ids")
	private UUID[] mediaIds;

}
