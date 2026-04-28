package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InteractionEntityTest {

    @Test
    void testInteractionEntityConstructor() {
        // Given
        LocalDateTime createdAt = LocalDateTime.now();

        // When
        InteractionEntity interactionEntity = new ConcreteInteractionEntity(createdAt);

        // Then
        assertNotNull(interactionEntity);
        assertNotNull(interactionEntity.getId());
        assertEquals(createdAt, interactionEntity.getCreatedAt());
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
        assertEquals(id, interactionEntity.getId());
        assertEquals(createdAt, interactionEntity.getCreatedAt());
    }

    private static class ConcreteInteractionEntity extends InteractionEntity {
        public ConcreteInteractionEntity() {
        }

        public ConcreteInteractionEntity(LocalDateTime createdAt) {
            super.setId(UUID.randomUUID());
            super.setCreatedAt(createdAt);
        }
    }

}