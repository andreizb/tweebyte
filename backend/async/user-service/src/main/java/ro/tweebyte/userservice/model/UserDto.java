/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class UserDto {

	@JsonProperty("id")
	private UUID id;

	@JsonProperty("user_name")
	private String userName;

	@JsonProperty("email")
	private String email;

	@JsonProperty("biography")
	private String biography;

	@JsonProperty("is_private")
	private Boolean isPrivate;

	@JsonProperty("birth_date")
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private LocalDate birthDate;

	@JsonProperty("created_at")
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private LocalDateTime createdAt;

	@JsonProperty("profile_picture_id")
	private UUID profilePictureId;

	@JsonProperty("following")
	private Long following;

	@JsonProperty("followers")
	private Long followers;

	@JsonProperty("tweets")
	private List<TweetDto> tweets;

}
