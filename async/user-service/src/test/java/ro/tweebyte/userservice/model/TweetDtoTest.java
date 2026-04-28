package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

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
    void getContent() {
        String content = "Test content";
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
        mentions.add(new TweetDto.MentionDto());
        TweetDto tweetDto = new TweetDto();
        tweetDto.setMentions(mentions);
        assertEquals(mentions, tweetDto.getMentions());
    }

    @Test
    void getHashtags() {
        Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
        hashtags.add(new TweetDto.HashtagDto());
        TweetDto tweetDto = new TweetDto();
        tweetDto.setHashtags(hashtags);
        assertEquals(hashtags, tweetDto.getHashtags());
    }

    @Test
    void getLikesCount() {
        Long likesCount = 10L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setLikesCount(likesCount);
        assertEquals(likesCount, tweetDto.getLikesCount());
    }

    @Test
    void getRepliesCount() {
        Long repliesCount = 5L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setRepliesCount(repliesCount);
        assertEquals(repliesCount, tweetDto.getRepliesCount());
    }

    @Test
    void getRetweetsCount() {
        Long retweetsCount = 15L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setRetweetsCount(retweetsCount);
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
    }

    @Test
    void getTopReply() {
        TweetDto.ReplyDto topReply = new TweetDto.ReplyDto();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setTopReply(topReply);
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setId(id);
        assertEquals(id, tweetDto.getId());
    }

    @Test
    void setContent() {
        String content = "Test content";
        TweetDto tweetDto = new TweetDto();
        tweetDto.setContent(content);
        assertEquals(content, tweetDto.getContent());
    }

    @Test
    void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setCreatedAt(createdAt);
        assertEquals(createdAt, tweetDto.getCreatedAt());
    }

    @Test
    void setMentions() {
        Set<TweetDto.MentionDto> mentions = new HashSet<>();
        mentions.add(new TweetDto.MentionDto());
        TweetDto tweetDto = new TweetDto();
        tweetDto.setMentions(mentions);
        assertEquals(mentions, tweetDto.getMentions());
    }

    @Test
    void setHashtags() {
        Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
        hashtags.add(new TweetDto.HashtagDto());
        TweetDto tweetDto = new TweetDto();
        tweetDto.setHashtags(hashtags);
        assertEquals(hashtags, tweetDto.getHashtags());
    }

    @Test
    void setLikesCount() {
        Long likesCount = 10L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setLikesCount(likesCount);
        assertEquals(likesCount, tweetDto.getLikesCount());
    }

    @Test
    void setRepliesCount() {
        Long repliesCount = 5L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setRepliesCount(repliesCount);
        assertEquals(repliesCount, tweetDto.getRepliesCount());
    }

    @Test
    void setRetweetsCount() {
        Long retweetsCount = 15L;
        TweetDto tweetDto = new TweetDto();
        tweetDto.setRetweetsCount(retweetsCount);
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
    }

    @Test
    void setTopReply() {
        TweetDto.ReplyDto topReply = new TweetDto.ReplyDto();
        TweetDto tweetDto = new TweetDto();
        tweetDto.setTopReply(topReply);
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void MentionDto_getters() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "Mention text";
        TweetDto.MentionDto mentionDto = new TweetDto.MentionDto(id, userId, text);
        assertEquals(id, mentionDto.getId());
        assertEquals(userId, mentionDto.getUserId());
        assertEquals(text, mentionDto.getText());
    }

    @Test
    void HashtagDto_getters() {
        UUID id = UUID.randomUUID();
        String text = "Hashtag text";
        TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto(id, text);
        assertEquals(id, hashtagDto.getId());
        assertEquals(text, hashtagDto.getText());
    }

    @Test
    void ReplyDto_getters() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Reply content";
        LocalDateTime createdAt = LocalDateTime.now();
        Long likesCount = 5L;
        List<TweetDto.LikeDto> likes = Collections.singletonList(new TweetDto.LikeDto(id));
        TweetDto.ReplyDto replyDto = new TweetDto.ReplyDto(id, userId, content, createdAt, likesCount, likes);
        assertEquals(id, replyDto.getId());
        assertEquals(userId, replyDto.getUserId());
        assertEquals(content, replyDto.getContent());
        assertEquals(createdAt, replyDto.getCreatedAt());
        assertEquals(likesCount, replyDto.getLikesCount());
        assertEquals(likes, replyDto.getLikes());
    }

    @Test
    void LikeDto_getters() {
        UUID id = UUID.randomUUID();
        TweetDto.LikeDto likeDto = new TweetDto.LikeDto(id);
        assertEquals(id, likeDto.getId());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        Set<TweetDto.HashtagDto> hashtags = Set.of(new TweetDto.HashtagDto(UUID.randomUUID(), "hashtag"));

        TweetDto tweet = new TweetDto(id, "content", createdAt, null, hashtags, 10L, 5L, 2L, null);

        assertNotNull(tweet);
        assertEquals(id, tweet.getId());
        assertEquals("content", tweet.getContent());
        assertEquals(createdAt, tweet.getCreatedAt());
        assertEquals(hashtags, tweet.getHashtags());
        assertEquals(10L, tweet.getLikesCount());
        assertEquals(5L, tweet.getRepliesCount());
        assertEquals(2L, tweet.getRetweetsCount());
    }

    @Test
    void testBuilder() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        Set<TweetDto.HashtagDto> hashtags = Set.of(new TweetDto.HashtagDto(UUID.randomUUID(), "hashtag"));

        TweetDto tweet = TweetDto.builder()
                .id(id)
                .content("content")
                .createdAt(createdAt)
                .hashtags(hashtags)
                .likesCount(10L)
                .repliesCount(5L)
                .retweetsCount(2L)
                .build();

        assertNotNull(tweet);
        assertEquals(id, tweet.getId());
        assertEquals("content", tweet.getContent());
        assertEquals(createdAt, tweet.getCreatedAt());
        assertEquals(hashtags, tweet.getHashtags());
        assertEquals(10L, tweet.getLikesCount());
        assertEquals(5L, tweet.getRepliesCount());
        assertEquals(2L, tweet.getRetweetsCount());
    }

}