package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TweetMapperTest {

    private TweetMapper tweetMapper;

    @Mock
    private MentionMapper mentionMapper;

    @Mock
    private HashtagMapper hashtagMapper;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        tweetMapper = new TweetMapperImpl();

        injectDependencies(tweetMapper, "mentionMapper", mentionMapper);
        injectDependencies(tweetMapper, "hashtagMapper", hashtagMapper);
    }

    private void injectDependencies(Object target, String fieldName, Object dependency) throws Exception {
        Field field = TweetMapper.class.getDeclaredField(fieldName); // Access fields in the abstract class
        field.setAccessible(true);
        field.set(target, dependency);
    }

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
    void testMapEntityToDtoWithUserDto() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Tweet Content");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        UserDto userDto = new UserDto(UUID.randomUUID(), "testuser", true, LocalDateTime.now());

        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity, userDto);

        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(userDto, tweetDto.getUser());
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
        assertEquals(likesCount, tweetDto.getLikesCount());
        assertEquals(repliesCount, tweetDto.getRepliesCount());
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
        assertEquals(topReply, tweetDto.getTopReply());
    }

    @Test
    void testMapCreationRequestToEntity() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setUserId(UUID.randomUUID());
        request.setContent("Test Content");

        TweetEntity tweetEntity = tweetMapper.mapCreationRequestToEntity(request);

        assertNotNull(tweetEntity);
        assertEquals(request.getUserId(), tweetEntity.getUserId());
        assertEquals(request.getContent(), tweetEntity.getContent());
        assertNotNull(tweetEntity.getId());
        assertNotNull(tweetEntity.getCreatedAt());
    }

    @Test
    void testMapEntityToCreationDto() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());

        TweetDto tweetDto = tweetMapper.mapEntityToCreationDto(tweetEntity);

        assertNotNull(tweetDto);
        assertEquals(tweetEntity.getId(), tweetDto.getId());
    }

    @Test
    void testMapEntityToCreationDtoWithNullEntity() {
        TweetDto tweetDto = tweetMapper.mapEntityToCreationDto(null);

        assertNull(tweetDto);
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
        assertEquals(tweetEntity.getId(), updatedEntity.getId()); // Ensure ID remains unchanged
    }

    @Test
    void testMapUpdateRequestToEntityWithNullContent() {
        TweetUpdateRequest request = new TweetUpdateRequest();
        request.setContent(null);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent("Old Content");

        TweetEntity updatedEntity = tweetMapper.mapUpdateRequestToEntity(request, tweetEntity);

        assertNotNull(updatedEntity);
        assertEquals("Old Content", updatedEntity.getContent());
    }

    @Test
    void testMapEntityToDto_WithLikesRepliesRetweetsMentionsHashtags() {
        // Given
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Sample Tweet");
        tweetEntity.setCreatedAt(LocalDateTime.now());

        Long likesCount = 10L;
        Long repliesCount = 5L;
        Long retweetsCount = 3L;

        ReplyDto topReply = new ReplyDto();
        topReply.setContent("Top Reply");

        HashtagEntity hashtag = new HashtagEntity(UUID.randomUUID(), "#example", true);
        MentionEntity mention = new MentionEntity(UUID.randomUUID(), UUID.randomUUID(), "@user", tweetEntity.getId(), true);

        List<HashtagEntity> hashtags = Collections.singletonList(hashtag);
        List<MentionEntity> mentions = Collections.singletonList(mention);

        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setText("#example");
        MentionDto mentionDto = new MentionDto();
        mentionDto.setText("@user");

        // Mock behavior
        when(hashtagMapper.mapEntityToDto(hashtag)).thenReturn(hashtagDto);
        when(mentionMapper.mapEntityToDto(mention)).thenReturn(mentionDto);

        // When
        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, topReply, hashtags, mentions);

        // Assert
        assertNotNull(tweetDto);
        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(likesCount, tweetDto.getLikesCount());
        assertEquals(repliesCount, tweetDto.getRepliesCount());
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
        assertEquals(topReply, tweetDto.getTopReply());
        assertNotNull(tweetDto.getHashtags());
        assertEquals(1, tweetDto.getHashtags().size());
        assertEquals("#example", tweetDto.getHashtags().iterator().next().getText());
        assertNotNull(tweetDto.getMentions());
        assertEquals(1, tweetDto.getMentions().size());
        assertEquals("@user", tweetDto.getMentions().iterator().next().getText());
    }

    @Test
    void testMapEntityToDto_WithRepliesMentionsHashtags() {
        // Given
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Sample Tweet");
        tweetEntity.setCreatedAt(LocalDateTime.now());

        Long likesCount = 10L;
        Long repliesCount = 5L;
        Long retweetsCount = 3L;

        ReplyDto topReply = new ReplyDto();
        topReply.setContent("Top Reply");

        HashtagEntity hashtag = new HashtagEntity(UUID.randomUUID(), "#example", true);
        MentionEntity mention = new MentionEntity(UUID.randomUUID(), UUID.randomUUID(), "@user", tweetEntity.getId(), true);

        List<HashtagEntity> hashtags = Collections.singletonList(hashtag);
        List<MentionEntity> mentions = Collections.singletonList(mention);

        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setText("#example");
        MentionDto mentionDto = new MentionDto();
        mentionDto.setText("@user");

        // Mock behavior
        when(hashtagMapper.mapEntityToDto(any(HashtagEntity.class))).thenReturn(hashtagDto);
        when(mentionMapper.mapEntityToDto(any(MentionEntity.class))).thenReturn(mentionDto);

        // When
        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, topReply, hashtags, mentions);

        // Assertions
        assertNotNull(tweetDto);
        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(likesCount, tweetDto.getLikesCount());
        assertEquals(repliesCount, tweetDto.getRepliesCount());
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
        assertEquals(topReply, tweetDto.getTopReply());

        assertNotNull(tweetDto.getHashtags());
        assertEquals(1, tweetDto.getHashtags().size());
        assertEquals("#example", tweetDto.getHashtags().iterator().next().getText()); // Ensures the mapped hashtag is correct

        assertNotNull(tweetDto.getMentions());
        assertEquals(1, tweetDto.getMentions().size());
        assertEquals("@user", tweetDto.getMentions().iterator().next().getText()); // Ensures the mapped mention is correct
    }

    @Test
    void testMapEntityToDto_WithLikesRepliesRetweetsMentionsHashtagsAndReplies() {
        // Given
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("Sample Tweet");
        tweetEntity.setCreatedAt(LocalDateTime.now());

        Long likesCount = 15L;
        Long repliesCount = 7L;
        Long retweetsCount = 4L;

        ReplyDto reply1 = new ReplyDto();
        reply1.setContent("First Reply");
        ReplyDto reply2 = new ReplyDto();
        reply2.setContent("Second Reply");
        List<ReplyDto> replies = Arrays.asList(reply1, reply2);

        MentionEntity mention = new MentionEntity(UUID.randomUUID(), UUID.randomUUID(), "@mentionedUser", tweetEntity.getId(), true);
        HashtagEntity hashtag = new HashtagEntity(UUID.randomUUID(), "#hashtag", true);

        List<MentionEntity> mentions = Collections.singletonList(mention);
        List<HashtagEntity> hashtags = Collections.singletonList(hashtag);

        MentionDto mentionDto = new MentionDto();
        mentionDto.setText("@mentionedUser");
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setText("#hashtag");

        // Mock behavior
        when(mentionMapper.mapEntityToDto(any(MentionEntity.class))).thenReturn(mentionDto);
        when(hashtagMapper.mapEntityToDto(any(HashtagEntity.class))).thenReturn(hashtagDto);

        // When
        TweetDto tweetDto = tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, replies, mentions, hashtags);

        // Assertions
        assertNotNull(tweetDto);
        assertEquals(tweetEntity.getId(), tweetDto.getId());
        assertEquals(tweetEntity.getContent(), tweetDto.getContent());
        assertEquals(likesCount, tweetDto.getLikesCount());
        assertEquals(repliesCount, tweetDto.getRepliesCount());
        assertEquals(retweetsCount, tweetDto.getRetweetsCount());
        assertEquals(replies, tweetDto.getReplies());

        assertNotNull(tweetDto.getMentions());
        assertEquals(1, tweetDto.getMentions().size());
        assertEquals("@mentionedUser", tweetDto.getMentions().iterator().next().getText());

        assertNotNull(tweetDto.getHashtags());
        assertEquals(1, tweetDto.getHashtags().size());
        assertEquals("#hashtag", tweetDto.getHashtags().iterator().next().getText());
    }

}
