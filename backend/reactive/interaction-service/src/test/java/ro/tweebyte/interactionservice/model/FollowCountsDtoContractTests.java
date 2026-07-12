/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowCountsDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void allArgsConstructorGroupsFollowerAndFollowingCounts() {
		FollowCountsDto counts = new FollowCountsDto(12L, 7L);

		assertThat(counts.getFollowers()).isEqualTo(12L);
		assertThat(counts.getFollowing()).isEqualTo(7L);
	}

	@Test
	void chainSettersReturnSameInstance() {
		FollowCountsDto counts = new FollowCountsDto();

		FollowCountsDto returned = counts.setFollowers(3L).setFollowing(4L);

		assertThat(returned).isSameAs(counts);
		assertThat(counts).isEqualTo(new FollowCountsDto(3L, 4L));
	}

	@Test
	void jsonUsesFollowersAndFollowingNames() throws JsonProcessingException {
		String json = this.objectMapper.writeValueAsString(new FollowCountsDto(21L, 34L));

		assertThat(json).contains("\"followers\":21", "\"following\":34");
	}

	@Test
	void jsonDeserializationIgnoresUnknownFields() throws JsonProcessingException {
		FollowCountsDto counts = this.objectMapper
			.readValue("{\"followers\":5,\"following\":9,\"ignored\":true}", FollowCountsDto.class);

		assertThat(counts.getFollowers()).isEqualTo(5L);
		assertThat(counts.getFollowing()).isEqualTo(9L);
	}

}
