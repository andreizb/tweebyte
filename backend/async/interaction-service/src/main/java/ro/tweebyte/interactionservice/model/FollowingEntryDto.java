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

/**
 * Lean per-row projection of the {@code GET /follows/{id}/following} response — the
 * followed user's identity plus when the edge was created. It deliberately omits the
 * three fields {@link FollowDto} carries that this list never needs: the internal
 * follow-row {@code id} (relationship metadata, used only by the follow-request
 * accept/reject flow), {@code follower_id} (constant — it is always the path user), and
 * {@code status} (constant — this endpoint only returns ACCEPTED edges). The smaller
 * cached value is cheaper for the single Lettuce IO thread to frame/decode on every hit,
 * which is the dominant cost of this read-heavy, ~90%-hit workload.
 *
 * @param followedId the followed user's id
 * @param userName the followed user's display name
 * @param createdAt when the follow edge was created
 * @author Andrei Zbarcea
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowingEntryDto(@JsonProperty("followed_id") UUID followedId,
		@JsonProperty("user_name") String userName,
		@JsonProperty("created_at") @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt) {
}
