/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusTests {

	@Test
	void valueOfPending() {
		assertThat(Status.valueOf("PENDING")).isEqualTo(Status.PENDING);
	}

	@Test
	void valueOfAccepted() {
		assertThat(Status.valueOf("ACCEPTED")).isEqualTo(Status.ACCEPTED);
	}

	@Test
	void valueOfRejected() {
		assertThat(Status.valueOf("REJECTED")).isEqualTo(Status.REJECTED);
	}

	@Test
	void valuesContainsAllThree() {
		Status[] values = Status.values();
		assertThat(values).hasSize(3);
	}

	@Test
	void valueOfThrowsForInvalid() {
		assertThatThrownBy(() -> Status.valueOf("BOGUS")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nameMatchesEnumConstant() {
		assertThat(Status.PENDING.name()).isEqualTo("PENDING");
		assertThat(Status.ACCEPTED.name()).isEqualTo("ACCEPTED");
		assertThat(Status.REJECTED.name()).isEqualTo("REJECTED");
	}

	@Test
	void valuesArrayNotNull() {
		assertThat(Status.values()).isNotNull();
	}

}
