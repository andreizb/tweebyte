package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage tests for ReplyService — covers null-tweet, mismatched-user
 * delete, missing-reply update, and empty top-reply page arms not exercised
 * by ReplyServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class ReplyServiceBranchTest {

    @Mock
    private TweetService tweetService;

    @Mock
    private UserService userService;

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private ReplyMapper replyMapper;

    @InjectMocks
    private ReplyService replyService;

    @Test
    void createReply_nullTweet_throws() {
        ReplyCreateRequest request = new ReplyCreateRequest();
        when(tweetService.getTweetSummary(any())).thenReturn(null);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> replyService.createReply(request).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void updateReply_notFound_throws() {
        ReplyUpdateRequest request = new ReplyUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setUserId(UUID.randomUUID());

        when(replyRepository.findById(request.getId())).thenReturn(Optional.empty());

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> replyService.updateReply(request).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void updateReply_userMismatch_doesNotSave() {
        // Non-author update raises IllegalArgumentException, so the
        // FE-equivalence scenario "Updating another user's reply is rejected"
        // returns 500 on both stacks.
        ReplyUpdateRequest request = new ReplyUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setUserId(UUID.randomUUID());

        ReplyEntity reply = new ReplyEntity();
        reply.setUserId(UUID.randomUUID()); // different user

        when(replyRepository.findById(request.getId())).thenReturn(Optional.of(reply));

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> replyService.updateReply(request).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void deleteReply_userMismatch_doesNotDelete() {
        UUID userId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ReplyEntity reply = new ReplyEntity();
        reply.setUserId(UUID.randomUUID()); // different user

        when(replyRepository.findById(replyId)).thenReturn(Optional.of(reply));

        // Non-author delete throws IllegalArgumentException (mapped to 500).
        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> replyService.deleteReply(userId, replyId).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(replyRepository, never()).deleteById(any());
    }

    @Test
    void deleteReply_replyMissing_noOp() {
        UUID userId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        when(replyRepository.findById(replyId)).thenReturn(Optional.empty());

        // missing reply now throws IllegalArgumentException (the test name's
        // "noOp" predates the fix).
        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> replyService.deleteReply(userId, replyId).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(replyRepository, never()).deleteById(any());
    }

    @Test
    void getTopReplyForTweet_emptyPage_returnsEmptyDto() throws Exception {
        UUID tweetId = UUID.randomUUID();
        when(replyRepository.findTopReplyByLikesForTweetId(any(), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        ReplyDto dto = replyService.getTopReplyForTweet(tweetId).get();

        assertNotNull(dto);
        verify(userService, never()).getUserSummary(any());
    }
}
