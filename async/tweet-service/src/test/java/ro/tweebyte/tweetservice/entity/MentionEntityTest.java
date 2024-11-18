package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MentionEntityTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setId(id);
        assertEquals(id, mentionEntity.getId());
    }

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setUserId(userId);
        assertEquals(userId, mentionEntity.getUserId());
    }

    @Test
    void getText() {
        String text = "mentionText";
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setText(text);
        assertEquals(text, mentionEntity.getText());
    }

    @Test
    void getTweetEntity() {
        TweetEntity tweetEntity = new TweetEntity();
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setTweetEntity(tweetEntity);
        assertEquals(tweetEntity, mentionEntity.getTweetEntity());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        assertNull(mentionEntity.getId()); // Initially null
        mentionEntity.setId(id);
        assertEquals(id, mentionEntity.getId());
    }

    @Test
    void setUserId() {
        UUID userId = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        assertNull(mentionEntity.getUserId()); // Initially null
        mentionEntity.setUserId(userId);
        assertEquals(userId, mentionEntity.getUserId());
    }

    @Test
    void setText() {
        String text = "mentionText";
        MentionEntity mentionEntity = new MentionEntity();
        assertNull(mentionEntity.getText()); // Initially null
        mentionEntity.setText(text);
        assertEquals(text, mentionEntity.getText());
    }

    @Test
    void setTweetEntity() {
        TweetEntity tweetEntity = new TweetEntity();
        MentionEntity mentionEntity = new MentionEntity();
        assertNull(mentionEntity.getTweetEntity()); // Initially null
        mentionEntity.setTweetEntity(tweetEntity);
        assertEquals(tweetEntity, mentionEntity.getTweetEntity());
    }

    @Test
    void builderTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "@testUser";
        TweetEntity tweetEntity = new TweetEntity();

        MentionEntity mentionEntity = MentionEntity.builder()
            .id(id)
            .userId(userId)
            .text(text)
            .tweetEntity(tweetEntity)
            .build();

        assertNotNull(mentionEntity);
        assertEquals(id, mentionEntity.getId());
        assertEquals(userId, mentionEntity.getUserId());
        assertEquals(text, mentionEntity.getText());
        assertEquals(tweetEntity, mentionEntity.getTweetEntity());
    }

    @Test
    void allArgsConstructorTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "@testUser";
        TweetEntity tweetEntity = new TweetEntity();

        MentionEntity mentionEntity = new MentionEntity(id, userId, text, tweetEntity);

        assertNotNull(mentionEntity);
        assertEquals(id, mentionEntity.getId());
        assertEquals(userId, mentionEntity.getUserId());
        assertEquals(text, mentionEntity.getText());
        assertEquals(tweetEntity, mentionEntity.getTweetEntity());
    }

}