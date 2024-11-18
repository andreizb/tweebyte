package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TweetDtoTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setId(id);
        assertEquals(id, tweetDto.getId());
    }

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setUserId(userId);
        assertEquals(userId, tweetDto.getUserId());
    }

    @Test
    void getContent() {
        String content = "Test tweet content";
        TweetDto tweetDto = new TweetDto();
        tweetDto.setContent(content);
        assertEquals(content, tweetDto.getContent());
    }

    @Test
    void getCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setCreatedAt(createdAt);
        assertEquals(createdAt, tweetDto.getCreatedAt());
    }

    @Test
    void getMentions() {
        Set<TweetDto.MentionDto> mentions = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setMentions(mentions);
        assertEquals(mentions, tweetDto.getMentions());
    }

    @Test
    void getHashtags() {
        Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setHashtags(hashtags);
        assertEquals(hashtags, tweetDto.getHashtags());
    }

    @Test
    void getLikesCount() {
        Long likesCount = 42L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setLikesCount(likesCount);
        assertEquals(likesCount, tweetDto.getLikesCount());
    }

    @Test
    void getRepliesCount() {
        Long repliesCount = 24L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setRepliesCount(repliesCount);
        assertEquals(repliesCount, tweetDto.getRepliesCount());
    }

    @Test
    void getRetweetsCount() {
        Long retweetsCount = 12L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setRetweetsCount(retweetsCount);
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
    }

    @Test
    void getTopReply() {
        ReplyDto topReply = new ReplyDto();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setTopReply(topReply);
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getId()); // Initially null
        tweetDto.setId(id);
        assertEquals(id, tweetDto.getId());
    }

    @Test
    void setUserId() {
        UUID userId = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getUserId()); // Initially null
        tweetDto.setUserId(userId);
        assertEquals(userId, tweetDto.getUserId());
    }

    @Test
    void setContent() {
        String content = "Test tweet content";
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getContent()); // Initially null
        tweetDto.setContent(content);
        assertEquals(content, tweetDto.getContent());
    }

    @Test
    void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getCreatedAt()); // Initially null
        tweetDto.setCreatedAt(createdAt);
        assertEquals(createdAt, tweetDto.getCreatedAt());
    }

    @Test
    void setMentions() {
        Set<TweetDto.MentionDto> mentions = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getMentions()); // Initially null
        tweetDto.setMentions(mentions);
        assertEquals(mentions, tweetDto.getMentions());
    }

    @Test
    void setHashtags() {
        Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getHashtags()); // Initially null
        tweetDto.setHashtags(hashtags);
        assertEquals(hashtags, tweetDto.getHashtags());
    }

    @Test
    void setLikesCount() {
        Long likesCount = 42L;
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getLikesCount()); // Initially null
        tweetDto.setLikesCount(likesCount);
        assertEquals(likesCount, tweetDto.getLikesCount());
    }

    @Test
    void setRepliesCount() {
        Long repliesCount = 24L;
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getRepliesCount()); // Initially null
        tweetDto.setRepliesCount(repliesCount);
        assertEquals(repliesCount, tweetDto.getRepliesCount());
    }

    @Test
    void setRetweetsCount() {
        Long retweetsCount = 12L;
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getRetweetsCount()); // Initially null
        tweetDto.setRetweetsCount(retweetsCount);
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
    }

    @Test
    void setTopReply() {
        ReplyDto topReply = new ReplyDto();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getTopReply()); // Initially null
        tweetDto.setTopReply(topReply);
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void testGetId() {
        UUID id = UUID.randomUUID();
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
        mentionDto.setId(id);
        assertEquals(id, mentionDto.getId());
    }

    @Test
    void testGetUserId() {
        UUID userId = UUID.randomUUID();
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
        mentionDto.setUserId(userId);
        assertEquals(userId, mentionDto.getUserId());
    }

    @Test
    void testGetText() {
        String text = "example";
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
        mentionDto.setText(text);
        assertEquals(text, mentionDto.getText());
    }

    @Test
    void testSetId() {
        UUID id = UUID.randomUUID();
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
        mentionDto.setId(id);
        assertEquals(id, mentionDto.getId());
    }

    @Test
    void testSetUserId() {
        UUID userId = UUID.randomUUID();
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
        mentionDto.setUserId(userId);
        assertEquals(userId, mentionDto.getUserId());
    }

    @Test
    void testSetText() {
        String text = "example";
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
        mentionDto.setText(text);
        assertEquals(text, mentionDto.getText());
    }

    @Test
    void testGetHashtagId() {
        UUID id = UUID.randomUUID();
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
        hashtagDto.setId(id);
        assertEquals(id, hashtagDto.getId());
    }

    @Test
    void testGetHashtagText() {
        String text = "example";
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
        hashtagDto.setText(text);
        assertEquals(text, hashtagDto.getText());
    }

    @Test
    void testGetCount() {
        Long count = 5L;
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
        hashtagDto.setCount(count);
        assertEquals(count, hashtagDto.getCount());
    }

    @Test
    void testSetHashtagId() {
        UUID id = UUID.randomUUID();
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
        hashtagDto.setId(id);
        assertEquals(id, hashtagDto.getId());
    }

    @Test
    void testSetHashtagText() {
        String text = "example";
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
        hashtagDto.setText(text);
        assertEquals(text, hashtagDto.getText());
    }

    @Test
    void testSetCount() {
        Long count = 5L;
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
        hashtagDto.setCount(count);
        assertEquals(count, hashtagDto.getCount());
    }

    @Test
    void testMentionDtoAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "mention text";

        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto(id, userId, text);

        assertEquals(id, mentionDto.getId());
        assertEquals(userId, mentionDto.getUserId());
        assertEquals(text, mentionDto.getText());
    }

    @Test
    void testMentionDtoBuilder() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "mention text";

        TweetDto.MentionDto mentionDto = TweetDto.MentionDto.builder()
            .id(id)
            .userId(userId)
            .text(text)
            .build();

        assertEquals(id, mentionDto.getId());
        assertEquals(userId, mentionDto.getUserId());
        assertEquals(text, mentionDto.getText());
    }

    @Test
    void testHashtagDtoAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        String text = "hashtag text";
        Long count = 10L;

        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto(id, text, count);

        assertEquals(id, hashtagDto.getId());
        assertEquals(text, hashtagDto.getText());
        assertEquals(count, hashtagDto.getCount());
    }

    @Test
    void testHashtagDtoBuilder() {
        UUID id = UUID.randomUUID();
        String text = "hashtag text";
        Long count = 10L;

        TweetDto.HashtagDto hashtagDto = TweetDto.HashtagDto.builder()
            .id(id)
            .text(text)
            .count(count)
            .build();

        assertEquals(id, hashtagDto.getId());
        assertEquals(text, hashtagDto.getText());
        assertEquals(count, hashtagDto.getCount());
    }

}