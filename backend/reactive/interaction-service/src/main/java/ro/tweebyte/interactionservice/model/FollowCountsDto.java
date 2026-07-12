/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Consolidated follower/following counts for one user, returned by the single
 * {@code GET /follows/{userId}/counts} endpoint so the user-profile read fans out one
 * call instead of two. The values are composed from the existing per-type
 * followers_count/following_count reads — this DTO only groups them.
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Accessors(chain = true)
public class FollowCountsDto implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("followers")
	private long followers;

	@JsonProperty("following")
	private long following;

}
