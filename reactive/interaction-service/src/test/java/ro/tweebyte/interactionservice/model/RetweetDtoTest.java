package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetweetDtoTest {

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        String content = "Test content";
        UserDto user = new UserDto();
        TweetDto tweet = new TweetDto();

        RetweetDto retweetDto = new RetweetDto()
            .setId(id)
            .setCreatedAt(createdAt)
            .setContent(content)
            .setUser(user)
            .setTweet(tweet);

        assertEquals(id, retweetDto.getId());
        assertEquals(createdAt, retweetDto.getCreatedAt());
        assertEquals(content, retweetDto.getContent());
        assertEquals(user, retweetDto.getUser());
        assertEquals(tweet, retweetDto.getTweet());

        // Test setters
        UUID newId = UUID.randomUUID();
        LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);
        String newContent = "New test content";
        UserDto newUser = new UserDto();
        TweetDto newTweet = new TweetDto();

        retweetDto.setId(newId);
        retweetDto.setCreatedAt(newCreatedAt);
        retweetDto.setContent(newContent);
        retweetDto.setUser(newUser);
        retweetDto.setTweet(newTweet);

        assertEquals(newId, retweetDto.getId());
        assertEquals(newCreatedAt, retweetDto.getCreatedAt());
        assertEquals(newContent, retweetDto.getContent());
        assertEquals(newUser, retweetDto.getUser());
        assertEquals(newTweet, retweetDto.getTweet());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        String content = "Test content";
        UserDto user = new UserDto();
        TweetDto tweet = new TweetDto();

        RetweetDto retweetDto = new RetweetDto(id, createdAt, content, user, tweet);

        assertEquals(id, retweetDto.getId());
        assertEquals(createdAt, retweetDto.getCreatedAt());
        assertEquals(content, retweetDto.getContent());
        assertEquals(user, retweetDto.getUser());
        assertEquals(tweet, retweetDto.getTweet());
    }

    @Test
    void testNoArgsConstructor() {
        RetweetDto retweetDto = new RetweetDto();

        assertNotNull(retweetDto);
    }

}