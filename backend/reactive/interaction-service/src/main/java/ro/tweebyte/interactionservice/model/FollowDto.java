/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class FollowDto {

	@JsonProperty("id")
	private UUID id;

	@JsonProperty("user_name")
	private String userName;

	@JsonProperty("follower_id")
	private UUID followerId;

	@JsonProperty("followed_id")
	private UUID followedId;

	@JsonProperty("created_at")
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private LocalDateTime createdAt;

	@JsonProperty("status")
	private Status status;

}
