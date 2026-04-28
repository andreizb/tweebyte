package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.*;

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
        Set<MentionDto> mentions = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setMentions(mentions);
        assertEquals(mentions, tweetDto.getMentions());
    }

    @Test
    void getHashtags() {
        Set<HashtagDto> hashtags = new HashSet<>();
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
    void getReplies() {
        List<ReplyDto> replies = new ArrayList<>();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setReplies(replies);
        assertEquals(replies, tweetDto.getReplies());
    }

    @Test
    void getUser() {
        UserDto user = new UserDto();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setUser(user);
        assertEquals(user, tweetDto.getUser());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getId());
        tweetDto.setId(id);
        assertEquals(id, tweetDto.getId());
    }

    @Test
    void setUserId() {
        UUID userId = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getUserId());
        tweetDto.setUserId(userId);
        assertEquals(userId, tweetDto.getUserId());
    }

    @Test
    void setContent() {
        String content = "Test tweet content";
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getContent());
        tweetDto.setContent(content);
        assertEquals(content, tweetDto.getContent());
    }

    @Test
    void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getCreatedAt());
        tweetDto.setCreatedAt(createdAt);
        assertEquals(createdAt, tweetDto.getCreatedAt());
    }

    @Test
    void setMentions() {
        Set<MentionDto> mentions = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getMentions());
        tweetDto.setMentions(mentions);
        assertEquals(mentions, tweetDto.getMentions());
    }

    @Test
    void setHashtags() {
        Set<HashtagDto> hashtags = new HashSet<>();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getHashtags());
        tweetDto.setHashtags(hashtags);
        assertEquals(hashtags, tweetDto.getHashtags());
    }

    @Test
    void setLikesCount() {
        Long likesCount = 42L;
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getLikesCount());
        tweetDto.setLikesCount(likesCount);
        assertEquals(likesCount, tweetDto.getLikesCount());
    }

    @Test
    void setRepliesCount() {
        Long repliesCount = 24L;
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getRepliesCount());
        tweetDto.setRepliesCount(repliesCount);
        assertEquals(repliesCount, tweetDto.getRepliesCount());
    }

    @Test
    void setRetweetsCount() {
        Long retweetsCount = 12L;
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getRetweetsCount());
        tweetDto.setRetweetsCount(retweetsCount);
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
    }

    @Test
    void setTopReply() {
        ReplyDto topReply = new ReplyDto();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getTopReply());
        tweetDto.setTopReply(topReply);
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void setReplies() {
        List<ReplyDto> replies = new ArrayList<>();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getReplies());
        tweetDto.setReplies(replies);
        assertEquals(replies, tweetDto.getReplies());
    }

    @Test
    void setUser() {
        UserDto user = new UserDto();
        TweetDto tweetDto = new TweetDto();
        assertNull(tweetDto.getUser());
        tweetDto.setUser(user);
        assertEquals(user, tweetDto.getUser());
    }
}