/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeDtoTests {

	@Test
	void testGetId() {
		UUID id = UUID.randomUUID();
		LikeDto likeDto = new LikeDto().setId(id);
		assertThat(likeDto.getId()).isEqualTo(id);
	}

	@Test
	void testSetId() {
		UUID id = UUID.randomUUID();
		LikeDto likeDto = new LikeDto();
		likeDto.setId(id);
		assertThat(likeDto.getId()).isEqualTo(id);
	}

}
