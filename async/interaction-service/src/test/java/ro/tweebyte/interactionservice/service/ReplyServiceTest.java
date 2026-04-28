package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReplyServiceTest {

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
    void testCreateReply() throws ExecutionException, InterruptedException {
        ReplyCreateRequest request = new ReplyCreateRequest();
        TweetDto tweet = new TweetDto();

        when(tweetService.getTweetSummary(any())).thenReturn(tweet);
        when(replyMapper.mapRequestToEntity(any())).thenReturn(new ReplyEntity());
        when(replyRepository.save(any())).thenReturn(new ReplyEntity());
        when(replyMapper.mapEntityToCreationDto(any())).thenReturn(new ReplyDto());

        replyService.createReply(request).get();

        verify(tweetService).getTweetSummary(any());
        verify(replyRepository).save(any());
    }

    @Test
    void testUpdateReply() throws ExecutionException, InterruptedException {
        ReplyUpdateRequest request = new ReplyUpdateRequest();
        request.setUserId(UUID.randomUUID());

        ReplyEntity reply = new ReplyEntity();
        reply.setUserId(request.getUserId());

        when(replyRepository.findById(any())).thenReturn(Optional.of(reply));

        replyService.updateReply(request).get();

        verify(replyRepository).findById(any());
        verify(replyRepository).save(any());
    }

    @Test
    void testDeleteReply() throws ExecutionException, InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();

        Optional<ReplyEntity> optReply = Optional.of(new ReplyEntity());
        optReply.get().setUserId(userId);

        when(replyRepository.findById(eq(replyId))).thenReturn(optReply);

        replyService.deleteReply(userId, replyId).get();

        verify(replyRepository).findById(replyId);
        verify(replyRepository).deleteById(replyId);
    }

    @Test
    void testGetReplyCountForTweet() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();

        when(replyRepository.countByTweetId(any())).thenReturn(5L);

        replyService.getReplyCountForTweet(tweetId).get();

        verify(replyRepository).countByTweetId(tweetId);
    }

    @Test
    void testGetTopReplyForTweet() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();

        Page<ReplyDto> page = new PageImpl<>(Collections.singletonList(new ReplyDto()));
        when(replyRepository.findTopReplyByLikesForTweetId(any(), any())).thenReturn(page);

        when(userService.getUserSummary(any())).thenReturn(new UserDto());

        replyService.getTopReplyForTweet(tweetId).get();

        verify(replyRepository).findTopReplyByLikesForTweetId(any(), any());
        verify(userService).getUserSummary(any());
    }

    @Test
    void testGetRepliesForTweet() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReplyEntity replyEntity = new ReplyEntity();
        replyEntity.setUserId(userId);
        replyEntity.setContent("Sample reply");
        List<ReplyEntity> replyEntities = List.of(replyEntity);

        UserDto userDto = new UserDto();
        userDto.setId(userId);
        userDto.setUserName("TestUser");

        ReplyDto replyDto = new ReplyDto();
        replyDto.setContent("Sample reply");
        replyDto.setUserName("TestUser");

        when(replyRepository.findByTweetIdOrderByCreatedAtDesc(tweetId)).thenReturn(replyEntities);
        when(userService.getUserSummary(userId)).thenReturn(userDto);
        when(replyMapper.mapEntityToDto(replyEntity, "TestUser")).thenReturn(replyDto);

        List<ReplyDto> result = replyService.getRepliesForTweet(tweetId).get();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Sample reply", result.get(0).getContent());
        assertEquals("TestUser", result.get(0).getUserName());

        verify(replyRepository).findByTweetIdOrderByCreatedAtDesc(tweetId);
        verify(userService).getUserSummary(userId);
        verify(replyMapper).mapEntityToDto(replyEntity, "TestUser");
    }

}
