/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.util;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the shared {@code #\\w+} / {@code @\\w+} extraction including the {@code content ==
 * null} guard arm (null content yields an empty set, never an NPE) that the create / update
 * paths rely on, plus the populated extraction both helpers delegate to.
 */
class TweetTokenParserTests {

	@Test
	void extractHashtagsReturnsEmptySetForNullContent() {
		// null content → the (content == null) guard returns an empty set rather than
		// constructing a Matcher over null.
		assertThat(TweetTokenParser.extractHashtags(null)).isEmpty();
	}

	@Test
	void extractMentionsReturnsEmptySetForNullContent() {
		assertThat(TweetTokenParser.extractMentions(null)).isEmpty();
	}

	@Test
	void extractHashtagsParsesEachDistinctTagWithoutHash() {
		Set<String> tags = TweetTokenParser.extractHashtags("loving #spring and #spring and #java");

		// Tokens are de-duplicated and stripped of the leading '#'.
		assertThat(tags).containsExactlyInAnyOrder("spring", "java");
	}

	@Test
	void extractMentionsParsesEachDistinctHandleWithoutAt() {
		Set<String> mentions = TweetTokenParser.extractMentions("hi @alice and @bob, cc @alice");

		assertThat(mentions).containsExactlyInAnyOrder("alice", "bob");
	}

	@Test
	void extractHashtagsReturnsEmptySetWhenNoTokensMatch() {
		// Non-null content with no '#' token still runs the matcher loop (zero iterations).
		assertThat(TweetTokenParser.extractHashtags("plain text, no tags")).isEmpty();
	}

}
