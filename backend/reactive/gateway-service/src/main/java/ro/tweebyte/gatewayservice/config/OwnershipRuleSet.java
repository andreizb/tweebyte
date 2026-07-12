/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Edge-level resource-ownership rules. Each rule binds an HTTP method to a gateway
 * path template whose {@code {userId}} segment names the acting user. On a mutating
 * request the gateway compares that segment to the authenticated token's subject and
 * rejects with 403 on mismatch. This is defense in depth: the services keep their own
 * authoritative ownership checks, and direct (non-gateway) calls are unaffected.
 *
 * <p>Patterns are matched with Spring's {@link PathPattern}, whose {@code {var}} captures
 * exactly one path segment. That keeps owner routes from colliding with sibling routes
 * that share a prefix but a different shape (for example {@code POST /likes/{userId}/...}
 * versus the batch {@code POST /likes/counts}).
 *
 * <p>A few read endpoints are shaped like an owned mutation but address the resource being
 * viewed, not the caller — for example {@code POST /follows/{userId}/profile-interactions},
 * where {@code {userId}} is the profile owner. Those are declared as non-owned carve-outs
 * and matched first, so the owner wildcard ({@code /follows/{userId}/{followedId}}) does not
 * 403 a viewer reading someone else's profile.
 *
 * @author Andrei Zbarcea
 */
@Component
public final class OwnershipRuleSet {

	private static final String OWNER_VARIABLE = "userId";

	private final Map<String, List<PathPattern>> nonOwnedPatternsByMethod;

	private final Map<String, List<PathPattern>> ownerPatternsByMethod;

	OwnershipRuleSet() {
		PathPatternParser parser = PathPatternParser.defaultInstance;
		this.nonOwnedPatternsByMethod = Map.of("POST",
				List.of(parser.parse("/interaction-service/follows/{userId}/profile-interactions")));
		this.ownerPatternsByMethod = Map.of("POST",
				List.of(parser.parse("/tweet-service/tweets/{userId}"),
						parser.parse("/tweet-service/tweets/ai/users/{userId}/conversations/{conversationId}/summarize"),
						parser.parse("/tweet-service/tweets/ai/users/{userId}/conversations/{conversationId}/buffered"),
						parser.parse(
								"/tweet-service/tweets/ai/users/{userId}/conversations/{conversationId}/summarize-with-tool"),
						parser.parse("/interaction-service/likes/{userId}/tweets/{tweetId}"),
						parser.parse("/interaction-service/likes/{userId}/replies/{replyId}"),
						parser.parse("/interaction-service/replies/{userId}"),
						parser.parse("/interaction-service/follows/{userId}/{followedId}"),
						parser.parse("/interaction-service/retweets/{userId}")),
				"PUT",
				List.of(parser.parse("/user-service/users/{userId}"),
						parser.parse("/tweet-service/tweets/{userId}/{tweetId}"),
						parser.parse("/interaction-service/replies/{userId}/{replyId}"),
						parser.parse("/interaction-service/follows/{userId}/{followRequestId}/{status}"),
						parser.parse("/interaction-service/retweets/{userId}/{retweetId}")),
				"DELETE",
				List.of(parser.parse("/user-service/users/{userId}"),
						parser.parse("/tweet-service/tweets/{userId}/{tweetId}"),
						parser.parse("/interaction-service/likes/{userId}/tweets/{tweetId}"),
						parser.parse("/interaction-service/likes/{userId}/replies/{replyId}"),
						parser.parse("/interaction-service/replies/{userId}/{replyId}"),
						parser.parse("/interaction-service/follows/{userId}/{followedId}"),
						parser.parse("/interaction-service/retweets/{userId}/{retweetId}")));
	}

	/**
	 * Resolves the userId the path declares as owner for a mutating route.
	 * @param method the request's HTTP method
	 * @param path the full gateway path, including the {@code /<service>} prefix
	 * @return the required owner userId, or empty when no ownership rule governs the
	 * request (the route is then allowed through on a valid token alone)
	 */
	public Optional<String> requiredOwner(String method, String path) {
		List<PathPattern> patterns = this.ownerPatternsByMethod.get(method);
		if (patterns == null || path == null) {
			return Optional.empty();
		}
		PathContainer container = PathContainer.parsePath(path);
		// Non-owned read routes that share an owner route's shape are exempted first.
		if (matchesAny(this.nonOwnedPatternsByMethod.get(method), container)) {
			return Optional.empty();
		}
		for (PathPattern pattern : patterns) {
			PathPattern.PathMatchInfo match = pattern.matchAndExtract(container);
			if (match != null) {
				return Optional.ofNullable(match.getUriVariables().get(OWNER_VARIABLE));
			}
		}
		return Optional.empty();
	}

	private static boolean matchesAny(List<PathPattern> patterns, PathContainer container) {
		if (patterns == null) {
			return false;
		}
		for (PathPattern pattern : patterns) {
			if (pattern.matches(container)) {
				return true;
			}
		}
		return false;
	}

}
