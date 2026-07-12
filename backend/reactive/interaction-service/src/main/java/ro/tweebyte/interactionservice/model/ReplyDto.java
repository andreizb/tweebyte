/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
public class ReplyDto {

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
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)
	private LocalDateTime createdAt;

	@JsonProperty("likes_count")
	private Long likesCount;

	@JsonProperty("likes")
	private List<LikeDto> likes;

	@JsonProperty("media_ids")
	private UUID[] mediaIds;

	public ReplyDto(UUID id, UUID userId, String content, LocalDateTime createdAt, Long likesCount) {
		this.id = id;
		this.userId = userId;
		this.content = content;
		this.createdAt = createdAt;
		this.likesCount = likesCount;
	}

}
