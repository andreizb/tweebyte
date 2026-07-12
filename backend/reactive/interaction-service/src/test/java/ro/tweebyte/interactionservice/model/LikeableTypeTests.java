/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeableTypeTests {

	@Test
	void valueOfTweet() {
		assertThat(LikeableType.valueOf("TWEET")).isEqualTo(LikeableType.TWEET);
	}

	@Test
	void valueOfReply() {
		assertThat(LikeableType.valueOf("REPLY")).isEqualTo(LikeableType.REPLY);
	}

	@Test
	void valuesContainsBoth() {
		LikeableType[] values = LikeableType.values();
		assertThat(values).hasSize(2);
	}

	@Test
	void nameMatchesEnumConstant() {
		assertThat(LikeableType.TWEET.name()).isEqualTo("TWEET");
		assertThat(LikeableType.REPLY.name()).isEqualTo("REPLY");
	}

	@Test
	void valuesArrayNotNull() {
		assertThat(LikeableType.values()).isNotNull();
	}

}
