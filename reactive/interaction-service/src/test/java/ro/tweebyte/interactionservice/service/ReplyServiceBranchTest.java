package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage suite for ReplyService — covers the unauthorized /
 * not-found branches (lines 43, 54) and the empty-top-reply
 * switchIfEmpty branch.
 */
@ExtendWith(MockitoExtension.class)
class ReplyServiceBranchTest {

    @InjectMocks
    private ReplyService replyService;

    @Mock
    private TweetService tweetService;

    @Mock
    private UserService userService;

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ReplyMapper replyMapper;

    private UUID userId;
    private UUID otherUserId;
    private UUID replyId;
    private ReplyEntity replyEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        replyId = UUID.randomUUID();
        replyEntity = ReplyEntity.builder()
            .id(replyId)
            .userId(userId)
            .build();
    }

    @Test
    void updateReply_DifferentUser_ErrorsUnauthorized() {
        // Covers the false-branch of "reply.userId.equals(request.userId)" in
        // ReplyService.updateReply.
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        req.setId(replyId);
        req.setUserId(otherUserId);
        req.setContent("hi");

        when(replyRepository.findById(eq(replyId))).thenReturn(Mono.just(replyEntity));

        StepVerifier.create(replyService.updateReply(req))
            .expectErrorMatches(e -> e instanceof IllegalArgumentException
                && e.getMessage().equals("Unauthorized or reply not found"))
            .verify();

        verify(replyRepository, never()).save(any());
    }

    @Test
    void deleteReply_DifferentUser_ErrorsUnauthorized() {
        // Covers the false-branch of "reply.userId.equals(userId)" in
        // ReplyService.deleteReply.
        when(replyRepository.findById(eq(replyId))).thenReturn(Mono.just(replyEntity));

        StepVerifier.create(replyService.deleteReply(otherUserId, replyId))
            .expectErrorMatches(e -> e instanceof IllegalArgumentException
                && e.getMessage().equals("Unauthorized or reply not found"))
            .verify();

        verify(replyRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void getTopReplyForTweet_NoReplies_ReturnsEmptyDto() {
        // Covers the switchIfEmpty branch in getTopReplyForTweet — the source
        // flux is empty so the .next() yields empty and the switchIfEmpty
        // emits a default ReplyDto.
        UUID tweetId = UUID.randomUUID();
        when(replyRepository.findTopReplyByLikesForTweetId(eq(tweetId))).thenReturn(Flux.empty());

        StepVerifier.create(replyService.getTopReplyForTweet(tweetId))
            .expectNextMatches(dto -> dto != null && dto.getContent() == null && dto.getId() == null)
            .verifyComplete();

        verify(userService, never()).getUserSummary(any());
    }

    @Test
    void updateReply_NotFound_RaisesError() {
        // When findById emits empty, switchIfEmpty raises
        // IllegalArgumentException("Unauthorized or reply not found") so the
        // controller surfaces a non-2xx — same observable behaviour as the
        // async stack on the same code path.
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        req.setId(replyId);
        req.setUserId(userId);
        when(replyRepository.findById(eq(replyId))).thenReturn(Mono.empty());

        StepVerifier.create(replyService.updateReply(req))
            .expectErrorMatches(t -> t instanceof IllegalArgumentException
                    && t.getMessage().contains("not found"))
            .verify();

        verify(replyRepository, never()).save(any());
    }
}
