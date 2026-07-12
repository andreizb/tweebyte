/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
public class TweetDto implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("id")
	private UUID id;

	@JsonProperty("user_id")
	private UUID userId;

	@JsonProperty("content")
	private String content;

	@JsonProperty("created_at")
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)
	private LocalDateTime createdAt;

	@JsonProperty("mentions")
	private Set<MentionDto> mentions;

	@JsonProperty("hashtags")
	private Set<HashtagDto> hashtags;

	@JsonProperty("likes_count")
	private Long likesCount;

	@JsonProperty("replies_count")
	private Long repliesCount;

	@JsonProperty("retweets_count")
	private Long retweetsCount;

	@JsonProperty("top_reply")
	private ReplyDto topReply;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder
	// Implements Serializable so the enclosing TweetDto stays JDK-serialisable
	// when Spring's RedisCacheManager (used by @Cacheable on TweetService)
	// writes it to Redis.
	public static class MentionDto implements Serializable {

		private static final long serialVersionUID = 1L;

		@JsonProperty("id")
		private UUID id;

		@JsonProperty("user_id")
		private UUID userId;

		@JsonProperty("text")
		private String text;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder
	// Same Serializable contract as MentionDto above.
	public static class HashtagDto implements Serializable {

		private static final long serialVersionUID = 1L;

		@JsonProperty("id")
		private UUID id;

		@JsonProperty("text")
		private String text;

		@JsonProperty("count")
		private Long count;

	}

}
