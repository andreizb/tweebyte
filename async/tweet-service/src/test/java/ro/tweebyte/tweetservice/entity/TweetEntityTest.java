package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TweetEntityTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(id);
        assertEquals(id, tweetEntity.getId());
    }

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setUserId(userId);
        assertEquals(userId, tweetEntity.getUserId());
    }

    @Test
    void getVersion() {
        Long version = 1L;
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setVersion(version);
        assertEquals(version, tweetEntity.getVersion());
    }

    @Test
    void getContent() {
        String content = "Tweet content";
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent(content);
        assertEquals(content, tweetEntity.getContent());
    }

    @Test
    void getCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setCreatedAt(createdAt);
        assertEquals(createdAt, tweetEntity.getCreatedAt());
    }

    @Test
    void getMentions() {
        MentionEntity mention1 = new MentionEntity();
        MentionEntity mention2 = new MentionEntity();
        Set<MentionEntity> mentions = new HashSet<>();
        mentions.add(mention1);
        mentions.add(mention2);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setMentions(mentions);

        assertEquals(mentions, tweetEntity.getMentions());
    }

    @Test
    void getHashtags() {
        HashtagEntity hashtag1 = new HashtagEntity();
        HashtagEntity hashtag2 = new HashtagEntity();
        Set<HashtagEntity> hashtags = new HashSet<>();
        hashtags.add(hashtag1);
        hashtags.add(hashtag2);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setHashtags(hashtags);

        assertEquals(hashtags, tweetEntity.getHashtags());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getId()); // Initially null
        tweetEntity.setId(id);
        assertEquals(id, tweetEntity.getId());
    }

    @Test
    void setUserId() {
        UUID userId = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getUserId()); // Initially null
        tweetEntity.setUserId(userId);
        assertEquals(userId, tweetEntity.getUserId());
    }

    @Test
    void setVersion() {
        Long version = 1L;
        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getVersion()); // Initially null
        tweetEntity.setVersion(version);
        assertEquals(version, tweetEntity.getVersion());
    }

    @Test
    void setContent() {
        String content = "Tweet content";
        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getContent()); // Initially null
        tweetEntity.setContent(content);
        assertEquals(content, tweetEntity.getContent());
    }

    @Test
    void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getCreatedAt()); // Initially null
        tweetEntity.setCreatedAt(createdAt);
        assertEquals(createdAt, tweetEntity.getCreatedAt());
    }

    @Test
    void setMentions() {
        MentionEntity mention1 = new MentionEntity();
        MentionEntity mention2 = new MentionEntity();
        Set<MentionEntity> mentions = new HashSet<>();
        mentions.add(mention1);
        mentions.add(mention2);

        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getMentions()); // Initially null
        tweetEntity.setMentions(mentions);
        assertEquals(mentions, tweetEntity.getMentions());
    }

    @Test
    void setHashtags() {
        HashtagEntity hashtag1 = new HashtagEntity();
        HashtagEntity hashtag2 = new HashtagEntity();
        Set<HashtagEntity> hashtags = new HashSet<>();
        hashtags.add(hashtag1);
        hashtags.add(hashtag2);

        TweetEntity tweetEntity = new TweetEntity();
        assertNull(tweetEntity.getHashtags()); // Initially null
        tweetEntity.setHashtags(hashtags);
        assertEquals(hashtags, tweetEntity.getHashtags());
    }

    @Test
    void builderTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test tweet content";
        LocalDateTime createdAt = LocalDateTime.now();
        Set<MentionEntity> mentions = new HashSet<>();
        Set<HashtagEntity> hashtags = new HashSet<>();

        TweetEntity tweetEntity = TweetEntity.builder()
            .id(id)
            .userId(userId)
            .content(content)
            .createdAt(createdAt)
            .mentions(mentions)
            .hashtags(hashtags)
            .build();

        assertNotNull(tweetEntity);
        assertEquals(id, tweetEntity.getId());
        assertEquals(userId, tweetEntity.getUserId());
        assertEquals(content, tweetEntity.getContent());
        assertEquals(createdAt, tweetEntity.getCreatedAt());
        assertEquals(mentions, tweetEntity.getMentions());
        assertEquals(hashtags, tweetEntity.getHashtags());
    }

    @Test
    void allArgsConstructorTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test tweet content";
        LocalDateTime createdAt = LocalDateTime.now();
        Set<MentionEntity> mentions = new HashSet<>();
        Set<HashtagEntity> hashtags = new HashSet<>();

        TweetEntity tweetEntity = new TweetEntity(id, userId, 0L, content, createdAt, mentions, hashtags);

        assertNotNull(tweetEntity);
        assertEquals(id, tweetEntity.getId());
        assertEquals(userId, tweetEntity.getUserId());
        assertEquals(content, tweetEntity.getContent());
        assertEquals(createdAt, tweetEntity.getCreatedAt());
        assertEquals(mentions, tweetEntity.getMentions());
        assertEquals(hashtags, tweetEntity.getHashtags());
    }

}
