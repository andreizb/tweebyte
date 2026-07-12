/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compact per-tweet projection served unpaged by {@code /tweets/user/{userId}/summary}.
 * Carries the tweet id plus its hashtag and mention texts (the recommender consumes the
 * ids for scoring; the token lists ride along for future recommendation signals).
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TweetSummaryDto implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("id")
	private UUID id;

	@JsonProperty("hashtags")
	private List<String> hashtags;

	@JsonProperty("mentions")
	private List<String> mentions;

}
