package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LikeDtoTest {

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        UserDto user = new UserDto();
        TweetDto tweet = new TweetDto();
        LocalDateTime createdAt = LocalDateTime.now();

        LikeDto likeDto = new LikeDto()
            .setId(id)
            .setUser(user)
            .setTweet(tweet)
            .setCreatedAt(createdAt);

        assertEquals(id, likeDto.getId());
        assertEquals(user, likeDto.getUser());
        assertEquals(tweet, likeDto.getTweet());
        assertEquals(createdAt, likeDto.getCreatedAt());

        UUID newId = UUID.randomUUID();
        UserDto newUser = new UserDto();
        TweetDto newTweet = new TweetDto();
        LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);

        likeDto.setId(newId);
        likeDto.setUser(newUser);
        likeDto.setTweet(newTweet);
        likeDto.setCreatedAt(newCreatedAt);

        assertEquals(newId, likeDto.getId());
        assertEquals(newUser, likeDto.getUser());
        assertEquals(newTweet, likeDto.getTweet());
        assertEquals(newCreatedAt, likeDto.getCreatedAt());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UserDto user = new UserDto();
        TweetDto tweet = new TweetDto();
        LocalDateTime createdAt = LocalDateTime.now();

        LikeDto likeDto = new LikeDto(id, user, tweet, createdAt);

        assertEquals(id, likeDto.getId());
        assertEquals(user, likeDto.getUser());
        assertEquals(tweet, likeDto.getTweet());
        assertEquals(createdAt, likeDto.getCreatedAt());
    }

    @Test
    void testNoArgsConstructor() {
        LikeDto likeDto = new LikeDto();

        assertNotNull(likeDto);
    }

}