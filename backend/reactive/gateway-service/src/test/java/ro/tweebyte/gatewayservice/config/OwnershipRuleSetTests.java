/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the edge-level ownership rule table. Confirms that mutating routes
 * carrying a {@code {userId}} owner segment surface that segment, that sibling batch
 * routes sharing a prefix are not mistaken for owner routes, and that reads and
 * non-owner mutations carry no ownership requirement.
 */
class OwnershipRuleSetTests {

	private final OwnershipRuleSet rules = new OwnershipRuleSet();

	@Test
	void putProfileIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("PUT", "/user-service/users/u1")).contains("u1");
	}

	@Test
	void deleteProfileIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("DELETE", "/user-service/users/u1")).contains("u1");
	}

	@Test
	void createTweetIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/tweet-service/tweets/u1")).contains("u1");
	}

	@Test
	void deleteTweetIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("DELETE", "/tweet-service/tweets/u1/t9")).contains("u1");
	}

	@Test
	void aiSummarizeIsOwnerScoped() {
		assertThat(this.rules
			.requiredOwner("POST", "/tweet-service/tweets/ai/users/u1/conversations/c2/summarize"))
			.contains("u1");
	}

	@Test
	void createFollowIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/interaction-service/follows/u1/u2")).contains("u1");
	}

	@Test
	void profileInteractionsReadIsNotOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/interaction-service/follows/u1/profile-interactions")).isEmpty();
	}

	@Test
	void likeTweetIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/interaction-service/likes/u1/tweets/t9")).contains("u1");
	}

	@Test
	void respondToFollowRequestIsOwnerScoped() {
		assertThat(this.rules.requiredOwner("PUT", "/interaction-service/follows/u1/fr5/ACCEPTED")).contains("u1");
	}

	@Test
	void batchLikeCountsIsNotOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/interaction-service/likes/counts")).isEmpty();
	}

	@Test
	void batchTopReplyIsNotOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/interaction-service/replies/tweet/top")).isEmpty();
	}

	@Test
	void mediaUploadIsNotOwnerScoped() {
		assertThat(this.rules.requiredOwner("POST", "/user-service/media")).isEmpty();
	}

	@Test
	void readsCarryNoOwnershipRequirement() {
		assertThat(this.rules.requiredOwner("GET", "/user-service/users/u1")).isEmpty();
	}

	@Test
	void ownerPatternDoesNotMatchAnotherMethod() {
		assertThat(this.rules.requiredOwner("GET", "/tweet-service/tweets/u1/t9")).isEmpty();
	}

	@Test
	void unknownMethodCarriesNoRequirement() {
		assertThat(this.rules.requiredOwner("PATCH", "/user-service/users/u1")).isEmpty();
	}

	@Test
	void nullPathCarriesNoRequirement() {
		assertThat(this.rules.requiredOwner("PUT", null)).isEmpty();
	}

}
