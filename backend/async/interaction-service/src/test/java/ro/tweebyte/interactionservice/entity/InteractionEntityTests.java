/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionEntityTests {

	@Test
	void testInteractionEntityConstructor() {
		// Given
		LocalDateTime createdAt = LocalDateTime.now();

		// When
		InteractionEntity interactionEntity = new ConcreteInteractionEntity(createdAt);

		// Then
		assertThat(interactionEntity).isNotNull();
		assertThat(interactionEntity.getId()).isNotNull();
		assertThat(interactionEntity.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testInteractionEntitySettersAndGetters() {
		// Given
		InteractionEntity interactionEntity = new ConcreteInteractionEntity();
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		// When
		interactionEntity.setId(id);
		interactionEntity.setCreatedAt(createdAt);

		// Then
		assertThat(interactionEntity.getId()).isEqualTo(id);
		assertThat(interactionEntity.getCreatedAt()).isEqualTo(createdAt);
	}

	private static class ConcreteInteractionEntity extends InteractionEntity {

		ConcreteInteractionEntity() {
		}

		ConcreteInteractionEntity(LocalDateTime createdAt) {
			super.setId(UUID.randomUUID());
			super.setCreatedAt(createdAt);
		}

	}

}
