/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared hashtag/mention extraction, mirroring the async-side parser. Patterns are
 * byte-for-byte identical with {@link ro.tweebyte.tweetservice.service.HashtagService}
 * and {@link ro.tweebyte.tweetservice.service.MentionService} on the creation path.
 *
 * <p>
 * Returns {@link LinkedHashSet} for deterministic iteration order; matters for tests that
 * assert specific result shapes.
 *
 * @author Andrei Zbarcea
 */
public final class TweetTokenParser {

	private static final Pattern HASHTAG_PATTERN = Pattern.compile("#\\w+");

	private static final Pattern MENTION_PATTERN = Pattern.compile("@\\w+");

	private TweetTokenParser() {
	}

	public static Set<String> extractHashtags(String content) {
		return extract(HASHTAG_PATTERN, content);
	}

	public static Set<String> extractMentions(String content) {
		return extract(MENTION_PATTERN, content);
	}

	private static Set<String> extract(Pattern pattern, String content) {
		Set<String> tokens = new LinkedHashSet<>();
		if (content == null) {
			return tokens;
		}
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			tokens.add(matcher.group().substring(1));
		}
		return tokens;
	}

}
