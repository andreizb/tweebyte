package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowEntityTest {

    @Test
    void testFollowEntityConstructor() {
        // Given
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        FollowEntity.Status status = FollowEntity.Status.PENDING;

        // When
        FollowEntity followEntity = new FollowEntity(followerId, followedId, status);

        // Then
        assertNotNull(followEntity);
        assertEquals(followerId, followEntity.getFollowerId());
        assertEquals(followedId, followEntity.getFollowedId());
        assertEquals(status, followEntity.getStatus());
    }

    @Test
    void testFollowEntitySettersAndGetters() {
        // Given
        FollowEntity followEntity = new FollowEntity();
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        FollowEntity.Status status = FollowEntity.Status.ACCEPTED;

        // When
        followEntity.setFollowerId(followerId);
        followEntity.setFollowedId(followedId);
        followEntity.setStatus(status);

        // Then
        assertEquals(followerId, followEntity.getFollowerId());
        assertEquals(followedId, followEntity.getFollowedId());
        assertEquals(status, followEntity.getStatus());
    }

    @Test
    void testFollowEntityStatusEnum() {
        // Given
        FollowEntity.Status pendingStatus = FollowEntity.Status.PENDING;
        FollowEntity.Status acceptedStatus = FollowEntity.Status.ACCEPTED;

        // Then
        assertEquals("PENDING", pendingStatus.name());
        assertEquals("ACCEPTED", acceptedStatus.name());
    }

}