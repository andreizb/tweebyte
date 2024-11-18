package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowDtoTest {

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        String userName = "testUser";
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        FollowDto.Status status = FollowDto.Status.ACCEPTED;

        FollowDto followDto = new FollowDto(id, userName, followerId, followedId, createdAt, status);

        assertEquals(id, followDto.getId());
        assertEquals(userName, followDto.getUserName());
        assertEquals(followerId, followDto.getFollowerId());
        assertEquals(followedId, followDto.getFollowedId());
        assertEquals(createdAt, followDto.getCreatedAt());
        assertEquals(status, followDto.getStatus());
    }

    @Test
    void testBuilder() {
        UUID id = UUID.randomUUID();
        String userName = "testUser";
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        FollowDto.Status status = FollowDto.Status.ACCEPTED;

        FollowDto followDto = new FollowDto()
            .setId(id)
            .setUserName(userName)
            .setFollowerId(followerId)
            .setFollowedId(followedId)
            .setCreatedAt(createdAt)
            .setStatus(status);

        assertEquals(id, followDto.getId());
        assertEquals(userName, followDto.getUserName());
        assertEquals(followerId, followDto.getFollowerId());
        assertEquals(followedId, followDto.getFollowedId());
        assertEquals(createdAt, followDto.getCreatedAt());
        assertEquals(status, followDto.getStatus());
    }

    @Test
    void testNoArgsConstructor() {
        FollowDto followDto = new FollowDto();
        assertNotNull(followDto);
    }

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        String userName = "testUser";
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        FollowDto.Status status = FollowDto.Status.ACCEPTED;

        FollowDto followDto = new FollowDto()
            .setId(id)
            .setUserName(userName)
            .setFollowerId(followerId)
            .setFollowedId(followedId)
            .setCreatedAt(createdAt)
            .setStatus(status);

        assertEquals(id, followDto.getId());
        assertEquals(userName, followDto.getUserName());
        assertEquals(followerId, followDto.getFollowerId());
        assertEquals(followedId, followDto.getFollowedId());
        assertEquals(createdAt, followDto.getCreatedAt());
        assertEquals(status, followDto.getStatus());

        // Test setters
        UUID newId = UUID.randomUUID();
        String newUserName = "newUser";
        UUID newFollowerId = UUID.randomUUID();
        UUID newFollowedId = UUID.randomUUID();
        LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);
        FollowDto.Status newStatus = FollowDto.Status.REJECTED;

        followDto.setId(newId);
        followDto.setUserName(newUserName);
        followDto.setFollowerId(newFollowerId);
        followDto.setFollowedId(newFollowedId);
        followDto.setCreatedAt(newCreatedAt);
        followDto.setStatus(newStatus);

        assertEquals(newId, followDto.getId());
        assertEquals(newUserName, followDto.getUserName());
        assertEquals(newFollowerId, followDto.getFollowerId());
        assertEquals(newFollowedId, followDto.getFollowedId());
        assertEquals(newCreatedAt, followDto.getCreatedAt());
        assertEquals(newStatus, followDto.getStatus());
    }

}