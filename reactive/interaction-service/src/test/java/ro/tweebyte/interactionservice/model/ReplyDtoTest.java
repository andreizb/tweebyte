package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReplyDtoTest {

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String userName = "TestUser";
        String content = "Test content";
        LocalDateTime createdAt = LocalDateTime.now();
        Long likesCount = 42L;

        List<LikeDto> likes = new ArrayList<>();
        likes.add(new LikeDto());

        ReplyDto replyDto = new ReplyDto()
            .setId(id)
            .setUserId(userId)
            .setUserName(userName)
            .setContent(content)
            .setCreatedAt(createdAt)
            .setLikesCount(likesCount)
            .setLikes(likes);

        assertEquals(id, replyDto.getId());
        assertEquals(userId, replyDto.getUserId());
        assertEquals(userName, replyDto.getUserName());
        assertEquals(content, replyDto.getContent());
        assertEquals(createdAt, replyDto.getCreatedAt());
        assertEquals(likesCount, replyDto.getLikesCount());
        assertEquals(likes, replyDto.getLikes());

        // Test setters
        UUID newId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        String newUserName = "NewUser";
        String newContent = "New test content";
        LocalDateTime newCreatedAt = LocalDateTime.now().minusHours(1);
        Long newLikesCount = 100L;

        List<LikeDto> newLikes = new ArrayList<>();
        newLikes.add(new LikeDto());

        replyDto.setId(newId);
        replyDto.setUserId(newUserId);
        replyDto.setUserName(newUserName);
        replyDto.setContent(newContent);
        replyDto.setCreatedAt(newCreatedAt);
        replyDto.setLikesCount(newLikesCount);
        replyDto.setLikes(newLikes);

        assertEquals(newId, replyDto.getId());
        assertEquals(newUserId, replyDto.getUserId());
        assertEquals(newUserName, replyDto.getUserName());
        assertEquals(newContent, replyDto.getContent());
        assertEquals(newCreatedAt, replyDto.getCreatedAt());
        assertEquals(newLikesCount, replyDto.getLikesCount());
        assertEquals(newLikes, replyDto.getLikes());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test content";
        LocalDateTime createdAt = LocalDateTime.now();
        Long likesCount = 42L;

        ReplyDto replyDto = new ReplyDto(id, userId, content, createdAt, likesCount);

        assertEquals(id, replyDto.getId());
        assertEquals(userId, replyDto.getUserId());
        assertEquals(content, replyDto.getContent());
        assertEquals(createdAt, replyDto.getCreatedAt());
        assertEquals(likesCount, replyDto.getLikesCount());
    }

    @Test
    void testNoArgsConstructor() {
        ReplyDto replyDto = new ReplyDto();

        assertNotNull(replyDto);
    }

}