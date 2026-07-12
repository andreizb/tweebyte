/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class TweetDto {

	@JsonProperty("id")
	private UUID id;

	@JsonProperty("content")
	private String content;

	@JsonProperty("created_at")
	@JsonFormat(shape = JsonFormat.Shape.STRING)
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
	public static class MentionDto {

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
	public static class HashtagDto {

		@JsonProperty("id")
		private UUID id;

		@JsonProperty("text")
		private String text;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder
	public static class ReplyDto {

		@JsonProperty("id")
		private UUID id;

		@JsonProperty("user_id")
		private UUID userId;

		@JsonProperty("user_name")
		private String userName;

		@JsonProperty("content")
		private String content;

		@JsonProperty("created_at")
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		private LocalDateTime createdAt;

		@JsonProperty("likes_count")
		private Long likesCount;

		@JsonProperty("likes")
		private List<LikeDto> likes;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Accessors(chain = true)
	public static class LikeDto {

		@JsonProperty("id")
		private UUID id;

	}

}
