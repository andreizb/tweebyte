package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ro.tweebyte.tweetservice.entity.*;
import ro.tweebyte.tweetservice.model.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TweetMapperTest {

    private final TweetMapper tweetMapper = Mappers.getMapper(TweetMapper.class);

    @Test
    void testMapEntityToDto() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Tweet Content");
        tweetEntity.setCreatedAt(LocalDateTime.now());

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity);

        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(tweetEntity.getCreatedAt(), tweetDto.getCreatedAt());
    }

    @Test
    void testMapEntityToDtoWithLikesRepliesRetweets() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Tweet Content");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        Long likesCount = 10L;
        Long repliesCount = 5L;
        Long retweetsCount = 7L;
        ReplyDto topReply = new ReplyDto();

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, topReply);

        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(tweetEntity.getCreatedAt(), tweetDto.getCreatedAt());
        assertEquals(likesCount, tweetDto.getLikesCount());
        assertEquals(repliesCount, tweetDto.getRepliesCount());
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void testMapEntityToDtoWithReplies() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Tweet Content");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        List<ReplyDto> replies = new ArrayList<>();

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity, null, null, null, replies);

        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(tweetEntity.getCreatedAt(), tweetDto.getCreatedAt());
        assertEquals(replies, tweetDto.getReplies());
    }

    @Test
    void mapCreationRequestToEntity_shouldMapCorrectly() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setUserId(UUID.randomUUID());
        request.setContent("Test Content");

        TweetEntity result = tweetMapper.mapCreationRequestToEntity(request);

        assertNotNull(result);
        assertEquals(request.getUserId(), result.getUserId());
        assertEquals(request.getContent(), result.getContent());
    }

    @Test
    void mapEntityToCreationDto_shouldMapCorrectly() {
        UUID id = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(id);

        TweetDto result = tweetMapper.mapEntityToCreationDto(tweetEntity);

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void mapEntityToCreationDto_shouldReturnNullWhenEntityIsNull() {
        TweetDto result = tweetMapper.mapEntityToCreationDto(null);

        assertNull(result);
    }

    @Test
    void testMapUpdateRequestToEntity() {
        TweetUpdateRequest request = new TweetUpdateRequest();
        request.setContent("Updated Content");

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent("Old Content");

        TweetEntity updatedEntity = tweetMapper.mapUpdateRequestToEntity(request, tweetEntity);

        assertNotNull(updatedEntity);
        assertEquals("Updated Content", updatedEntity.getContent());
    }

    @Test
    void testMapMentions() {
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setId(UUID.randomUUID());
        mentionEntity.setUserId(UUID.randomUUID());
        mentionEntity.setText("@user");

        Set<MentionEntity> mentions = new HashSet<>();
        mentions.add(mentionEntity);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setMentions(mentions);

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity);

        assertNull(tweetDto.getMentions());
    }

    @Test
    void testMapHashtags() {
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setId(UUID.randomUUID());
        hashtagEntity.setText("#hashtag");

        Set<HashtagEntity> hashtags = new HashSet<>();
        hashtags.add(hashtagEntity);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setHashtags(hashtags);

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity);

        assertNull(tweetDto.getHashtags());
    }

    @Test
    void testMapNullMentionsAndHashtags() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setMentions(null);
        tweetEntity.setHashtags(null);

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity);

        assertNull(tweetDto.getMentions());
        assertNull(tweetDto.getHashtags());
    }
}