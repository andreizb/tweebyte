/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.util;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TweetTokenParser}.
 * Covers all four branches: null content for both extractors, and
 * content-with-tokens for both.
 */
class TweetTokenParserTests {

	@Test
	void extractHashtags_nullContent_returnsEmptySet() {
		Set<String> result = TweetTokenParser.extractHashtags(null);
		assertThat(result).isEmpty();
	}

	@Test
	void extractHashtags_withHashtags_returnsTokens() {
		Set<String> result = TweetTokenParser.extractHashtags("Hello #world #java");
		assertThat(result).containsExactly("world", "java");
	}

	@Test
	void extractHashtags_noHashtags_returnsEmptySet() {
		Set<String> result = TweetTokenParser.extractHashtags("Hello world no hashtags");
		assertThat(result).isEmpty();
	}

	@Test
	void extractMentions_nullContent_returnsEmptySet() {
		Set<String> result = TweetTokenParser.extractMentions(null);
		assertThat(result).isEmpty();
	}

	@Test
	void extractMentions_withMentions_returnsTokens() {
		Set<String> result = TweetTokenParser.extractMentions("Hello @alice and @bob");
		assertThat(result).containsExactly("alice", "bob");
	}

	@Test
	void extractMentions_noMentions_returnsEmptySet() {
		Set<String> result = TweetTokenParser.extractMentions("plain tweet");
		assertThat(result).isEmpty();
	}

	@Test
	void extractHashtags_emptyContent_returnsEmptySet() {
		Set<String> result = TweetTokenParser.extractHashtags("");
		assertThat(result).isEmpty();
	}

	@Test
	void extractMentions_emptyContent_returnsEmptySet() {
		Set<String> result = TweetTokenParser.extractMentions("");
		assertThat(result).isEmpty();
	}

}
