package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Branch coverage — exercises the negative branches of LikeService that the
 * existing happy-path tests skip. Specifically the
 * "likeReply when reply does not exist" branch which reaches the
 * IllegalArgumentException error signal, and the "likeTweet" path with the
 * tweet-summary error (covers the .flatMap propagation).
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceBranchTest {

    @InjectMocks
    private LikeService likeService;

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

    private UUID userId;
    private UUID tweetId;
    private UUID replyId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tweetId = UUID.randomUUID();
        replyId = UUID.randomUUID();
    }

    @Test
    void likeReply_NotFound_RaisesIllegalArgument() {
        // hasElement() returns Mono<Boolean>(false) when findById emits empty —
        // exercises the !exists branch of likeReply.
        when(replyRepository.findById(eq(replyId))).thenReturn(Mono.empty());

        StepVerifier.create(likeService.likeReply(userId, replyId))
            .expectErrorMatches(e -> e instanceof IllegalArgumentException
                && e.getMessage().equals("Reply does not exist."))
            .verify();

        verify(replyRepository).findById(replyId);
        verify(likeRepository, never()).save(any());
    }

    @Test
    void likeTweet_TweetServiceErrors_PropagatesError() {
        // Covers the likeTweet error pass-through when the
        // upstream tweet-summary fails (e.g. tweet not found in the cache /
        // tweet-service). The save() must not be invoked.
        when(tweetService.getTweetSummary(eq(tweetId)))
            .thenReturn(Mono.error(new RuntimeException("tweet lookup failed")));

        StepVerifier.create(likeService.likeTweet(userId, tweetId))
            .expectError(RuntimeException.class)
            .verify();

        verify(tweetService).getTweetSummary(tweetId);
        verify(likeRepository, never()).save(any());
    }
}
