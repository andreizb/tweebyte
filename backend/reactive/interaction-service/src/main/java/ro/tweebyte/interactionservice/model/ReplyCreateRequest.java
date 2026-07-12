/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ReplyCreateRequest {

	// user_id is injected from the path by the controller, so it is intentionally
	// not validated here — it is always null in the request body at bind time.
	@NotNull
	@JsonProperty("tweet_id")
	private UUID tweetId;

	@JsonProperty("user_id")
	private UUID userId;

	@Size(max = 255)
	@JsonProperty("content")
	private String content;

	@JsonProperty("media_ids")
	private UUID[] mediaIds;

}
