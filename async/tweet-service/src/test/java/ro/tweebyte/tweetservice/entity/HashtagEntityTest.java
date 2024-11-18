package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HashtagEntityTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setId(id);
        assertEquals(id, hashtagEntity.getId());
    }

    @Test
    void getText() {
        String text = "exampleHashtag";
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setText(text);
        assertEquals(text, hashtagEntity.getText());
    }

    @Test
    void getTweets() {
        Set<TweetEntity> tweets = new HashSet<>();
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setTweets(tweets);
        assertEquals(tweets, hashtagEntity.getTweets());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        HashtagEntity hashtagEntity = new HashtagEntity();
        assertNull(hashtagEntity.getId()); // Initially null
        hashtagEntity.setId(id);
        assertEquals(id, hashtagEntity.getId());
    }

    @Test
    void setText() {
        String text = "exampleHashtag";
        HashtagEntity hashtagEntity = new HashtagEntity();
        assertNull(hashtagEntity.getText()); // Initially null
        hashtagEntity.setText(text);
        assertEquals(text, hashtagEntity.getText());
    }

    @Test
    void setTweets() {
        Set<TweetEntity> tweets = new HashSet<>();
        HashtagEntity hashtagEntity = new HashtagEntity();
        assertNull(hashtagEntity.getTweets()); // Initially null
        hashtagEntity.setTweets(tweets);
        assertEquals(tweets, hashtagEntity.getTweets());
    }

    @Test
    void builderTest() {
        UUID id = UUID.randomUUID();
        String text = "TestHashtag";
        Set<TweetEntity> tweets = new HashSet<>();

        HashtagEntity hashtagEntity = HashtagEntity.builder()
            .id(id)
            .text(text)
            .tweets(tweets)
            .build();

        assertNotNull(hashtagEntity);
        assertEquals(id, hashtagEntity.getId());
        assertEquals(text, hashtagEntity.getText());
        assertEquals(tweets, hashtagEntity.getTweets());
    }

    @Test
    void allArgsConstructorTest() {
        UUID id = UUID.randomUUID();
        String text = "TestHashtag";
        Set<TweetEntity> tweets = new HashSet<>();

        HashtagEntity hashtagEntity = new HashtagEntity(id, text, tweets);

        assertNotNull(hashtagEntity);
        assertEquals(id, hashtagEntity.getId());
        assertEquals(text, hashtagEntity.getText());
        assertEquals(tweets, hashtagEntity.getTweets());
    }

}