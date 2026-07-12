/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("tweet_hashtag")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TweetHashtagEntity {

	@Column("tweet_id")
	private UUID tweetId;

	@Column("hashtag_id")
	private UUID hashtagId;

}
