/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Consolidated follower/following counts deserialized from interaction-service's
 * {@code GET /follows/{userId}/counts}, letting the user-profile read fetch both counts in
 * one round-trip instead of two. The mapper still reads the two fields back into the same
 * followers_count / following_count profile fields.
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Accessors(chain = true)
public class FollowCountsDto {

	@JsonProperty("followers")
	private long followers;

	@JsonProperty("following")
	private long following;

}
