package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage focused tests for LikeService — exercises the negative paths
 * that the happy-path LikeServiceTest does not cover (null-tweet, missing
 * reply, etc.).
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceBranchTest {

    @Mock
    private UserService userService;

    @Mock
    private TweetService tweetService;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private LikeMapper likeMapper;

    @InjectMocks
    private LikeService likeService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tweetId = UUID.randomUUID();
    private final UUID replyId = UUID.randomUUID();

    @Test
    void likeTweet_nullTweet_throws() {
        // Negative branch: tweetService returns null → IllegalArgumentException wrapped
        when(tweetService.getTweetSummary(tweetId)).thenReturn(null);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> likeService.likeTweet(userId, tweetId).get());

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(likeRepository, never()).save(any());
    }

    @Test
    void likeReply_replyMissing_throws() {
        // Negative branch: reply not present → IllegalArgumentException wrapped
        // production now uses findById(replyId), not findByIdAndUserId.
        when(replyRepository.findById(replyId)).thenReturn(Optional.empty());

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> likeService.likeReply(userId, replyId).get());

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(likeRepository, never()).save(any());
    }

    @Test
    void likeReply_replyPresent_savesAndMaps() {
        // Positive branch — sister to negative above, ensures both arms hit.
        when(replyRepository.findById(replyId))
            .thenReturn(Optional.of(new ReplyEntity()));
        when(likeMapper.mapRequestToEntity(userId, replyId, LikeEntity.LikeableType.REPLY))
            .thenReturn(new LikeEntity());
        when(likeRepository.save(any(LikeEntity.class))).thenReturn(new LikeEntity());

        try {
            likeService.likeReply(userId, replyId).get();
        } catch (Exception e) {
            fail("should not throw");
        }
        verify(likeRepository).save(any(LikeEntity.class));
    }
}
