package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @InjectMocks
    private MentionService mentionService;

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private MentionMapper mentionMapper;

    @Mock
    private UserService userService;

    private TweetCreationRequest tweetRequest;
    private TweetEntity tweetEntity;

    @BeforeEach
    void setup() {
        tweetRequest = new TweetCreationRequest();
        tweetRequest.setId(UUID.randomUUID());
        tweetRequest.setContent("Hello @user1 @user2");

        tweetEntity = new TweetEntity();
        tweetEntity.setId(tweetRequest.getId());
        tweetEntity.setContent(tweetRequest.getContent());
    }

    @Test
    void handleTweetCreationMentions_Success() {
        when(tweetRepository.findById(tweetRequest.getId())).thenReturn(Mono.just(tweetEntity));
        when(userService.getUserId(any())).thenReturn(Mono.just(UUID.randomUUID()));
        when(mentionMapper.mapFieldsToEntity(any(), anyString(), any())).thenReturn(new MentionEntity());
        when(mentionRepository.save(any(MentionEntity.class))).thenReturn(Mono.just(new MentionEntity()));

        StepVerifier.create(mentionService.handleTweetCreationMentions(tweetRequest))
            .verifyComplete();

        verify(tweetRepository).findById(tweetRequest.getId());
        verify(userService, times(2)).getUserId(any());
        verify(mentionRepository, times(2)).save(any(MentionEntity.class));
    }

    @Test
    void handleTweetCreationMentions_TweetNotFound() {
        when(tweetRepository.findById(tweetRequest.getId())).thenReturn(Mono.empty());

        StepVerifier.create(mentionService.handleTweetCreationMentions(tweetRequest))
            .expectError(TweetNotFoundException.class)
            .verify();

        verify(tweetRepository).findById(tweetRequest.getId());
        verifyNoInteractions(userService);
    }

    @Test
    void handleTweetCreationMentions_UserNotFoundIsSwallowed() {
        when(tweetRepository.findById(tweetRequest.getId())).thenReturn(Mono.just(tweetEntity));
        when(userService.getUserId(any())).thenReturn(Mono.error(new UserNotFoundException("nope")));

        StepVerifier.create(mentionService.handleTweetCreationMentions(tweetRequest))
            .verifyComplete();

        verify(userService, times(2)).getUserId(any());
        verify(mentionRepository, never()).save(any(MentionEntity.class));
    }

    @Test
    void handleTweetCreationMentions_GenericErrorPropagates() {
        when(tweetRepository.findById(tweetRequest.getId())).thenReturn(Mono.just(tweetEntity));
        when(userService.getUserId(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(mentionService.handleTweetCreationMentions(tweetRequest))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void handleTweetUpdateMentions_UserNotFoundOnAddIsSwallowed() {
        // Empty existing mentions, two new mentions in tweetRequest content -> add path
        when(mentionRepository.findMentionsByTweetId(tweetRequest.getId())).thenReturn(Flux.empty());
        when(userService.getUserId(any())).thenReturn(Mono.error(new UserNotFoundException("nope")));

        StepVerifier.create(mentionService.handleTweetUpdateMentions(tweetRequest))
            .verifyComplete();

        verify(userService, times(2)).getUserId(any());
        verify(mentionRepository, never()).save(any());
    }

    @Test
    void handleTweetUpdateMentions_GenericErrorOnAddPropagates() {
        when(mentionRepository.findMentionsByTweetId(tweetRequest.getId())).thenReturn(Flux.empty());
        when(userService.getUserId(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(mentionService.handleTweetUpdateMentions(tweetRequest))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void handleTweetUpdateMentions_RemovesObsoleteMentions() {
        // Existing mention "user1" is in new content; existing "ghost" is not -> ghost should be removed.
        MentionEntity keep = new MentionEntity();
        keep.setText("user1");
        MentionEntity drop = new MentionEntity();
        drop.setText("ghost");
        when(mentionRepository.findMentionsByTweetId(tweetRequest.getId()))
            .thenReturn(Flux.just(keep, drop));
        when(userService.getUserId(any())).thenReturn(Mono.just(UUID.randomUUID()));
        when(mentionMapper.mapFieldsToEntity(any(), anyString())).thenReturn(new MentionEntity());
        when(mentionRepository.save(any())).thenReturn(Mono.just(new MentionEntity()));
        when(mentionRepository.delete(any(MentionEntity.class))).thenReturn(Mono.empty());

        StepVerifier.create(mentionService.handleTweetUpdateMentions(tweetRequest))
            .verifyComplete();

        // ghost is removed, user2 is added (user1 already exists, so 1 add only)
        verify(mentionRepository).delete(drop);
        verify(mentionRepository, times(1)).save(any());
    }

    @Test
    void handleTweetUpdateMentions_Success() {
        MentionEntity existingMention = new MentionEntity();
        existingMention.setText("user1");
        when(mentionMapper.mapFieldsToEntity(any(), anyString())).thenReturn(existingMention);
        when(mentionRepository.findMentionsByTweetId(tweetRequest.getId())).thenReturn(Flux.just(existingMention));
        when(userService.getUserId(any())).thenReturn(Mono.just(UUID.randomUUID()));
        when(mentionRepository.save(any())).thenReturn(Mono.just(existingMention));

        StepVerifier.create(mentionService.handleTweetUpdateMentions(tweetRequest))
            .verifyComplete();

        verify(mentionRepository).findMentionsByTweetId(tweetRequest.getId());
        verify(userService, times(1)).getUserId(any());
        verify(mentionRepository).save(any());
    }
}
