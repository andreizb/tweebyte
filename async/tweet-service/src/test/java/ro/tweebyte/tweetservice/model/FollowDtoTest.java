package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowDtoTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        FollowDto followDto = new FollowDto();
        followDto.setId(id);
        assertEquals(id, followDto.getId());
    }

    @Test
    void getUserName() {
        String userName = "testUser";
        FollowDto followDto = new FollowDto();
        followDto.setUserName(userName);
        assertEquals(userName, followDto.getUserName());
    }

    @Test
    void getFollowerId() {
        UUID followerId = UUID.randomUUID();
        FollowDto followDto = new FollowDto();
        followDto.setFollowerId(followerId);
        assertEquals(followerId, followDto.getFollowerId());
    }

    @Test
    void getFollowedId() {
        UUID followedId = UUID.randomUUID();
        FollowDto followDto = new FollowDto();
        followDto.setFollowedId(followedId);
        assertEquals(followedId, followDto.getFollowedId());
    }

    @Test
    void getCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        FollowDto followDto = new FollowDto();
        followDto.setCreatedAt(createdAt);
        assertEquals(createdAt, followDto.getCreatedAt());
    }

    @Test
    void getStatus() {
        FollowDto.Status status = FollowDto.Status.PENDING;
        FollowDto followDto = new FollowDto();
        followDto.setStatus(status);
        assertEquals(status, followDto.getStatus());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        FollowDto followDto = new FollowDto();
        assertNull(followDto.getId()); // Initially null
        followDto.setId(id);
        assertEquals(id, followDto.getId());
    }

    @Test
    void setUserName() {
        String userName = "testUser";
        FollowDto followDto = new FollowDto();
        assertNull(followDto.getUserName()); // Initially null
        followDto.setUserName(userName);
        assertEquals(userName, followDto.getUserName());
    }

    @Test
    void setFollowerId() {
        UUID followerId = UUID.randomUUID();
        FollowDto followDto = new FollowDto();
        assertNull(followDto.getFollowerId()); // Initially null
        followDto.setFollowerId(followerId);
        assertEquals(followerId, followDto.getFollowerId());
    }

    @Test
    void setFollowedId() {
        UUID followedId = UUID.randomUUID();
        FollowDto followDto = new FollowDto();
        assertNull(followDto.getFollowedId()); // Initially null
        followDto.setFollowedId(followedId);
        assertEquals(followedId, followDto.getFollowedId());
    }

    @Test
    void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        FollowDto followDto = new FollowDto();
        assertNull(followDto.getCreatedAt()); // Initially null
        followDto.setCreatedAt(createdAt);
        assertEquals(createdAt, followDto.getCreatedAt());
    }

    @Test
    void setStatus() {
        FollowDto.Status status = FollowDto.Status.PENDING;
        FollowDto followDto = new FollowDto();
        assertNull(followDto.getStatus()); // Initially null
        followDto.setStatus(status);
        assertEquals(status, followDto.getStatus());
    }
}
