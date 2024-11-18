package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
public class MentionServiceTest {

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private MentionMapper mentionMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private MentionService mentionService;

    @Test
    void testHandleTweetCreationMentions() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setId(UUID.randomUUID());

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent("This is a mention to @testUser.");
        tweetEntity.setMentions(new HashSet<>());

        when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.of(tweetEntity));
        when(userService.getUserId(any(String.class))).thenReturn(UUID.randomUUID());
        when(mentionMapper.mapFieldsToEntity(any(UUID.class), any(String.class), any(TweetEntity.class)))
            .thenReturn(new MentionEntity());

        mentionService.handleTweetCreationMentions(request);

        assertFalse(tweetEntity.getMentions().isEmpty());
    }

    @Test
    void testHandleTweetCreationMentionsWithTweetNotFound() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setId(UUID.randomUUID());

        when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(TweetNotFoundException.class, () -> mentionService.handleTweetCreationMentions(request));
    }

    @Test
    void testHandleTweetCreationMentionsWithUserNotFound() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setId(UUID.randomUUID());

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent("This is a mention to @testUser.");
        tweetEntity.setMentions(new HashSet<>());

        when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.of(tweetEntity));
        when(userService.getUserId(any(String.class))).thenThrow(new UserNotFoundException("User not found"));

        assertDoesNotThrow(() -> mentionService.handleTweetCreationMentions(request));

        assertTrue(tweetEntity.getMentions().isEmpty());
    }

}