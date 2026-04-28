package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LikeServiceTest {

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
    void testGetUserLikes() throws ExecutionException, InterruptedException {
        LikeEntity likeEntity = new LikeEntity();
        likeEntity.setLikeableId(tweetId);
        when(likeRepository.findByUserIdAndLikeableType(userId, LikeEntity.LikeableType.TWEET))
            .thenReturn(Collections.singletonList(likeEntity));
        when(tweetService.getTweetSummary(tweetId)).thenReturn(new TweetDto());
        when(likeMapper.mapToDto(any(), any(TweetDto.class))).thenReturn(new LikeDto());

        var result = likeService.getUserLikes(userId).get();

        verify(likeRepository).findByUserIdAndLikeableType(userId, LikeEntity.LikeableType.TWEET);
        verify(tweetService).getTweetSummary(tweetId);
        verify(likeMapper).mapToDto(any(LikeEntity.class), any(TweetDto.class));
        assertNotNull(result);
    }

    @Test
    void testGetTweetLikes() throws ExecutionException, InterruptedException {
        LikeEntity likeEntity = new LikeEntity();
        likeEntity.setUserId(userId);
        when(likeRepository.findByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET))
            .thenReturn(Collections.singletonList(likeEntity));
        when(userService.getUserSummary(userId)).thenReturn(new UserDto());
        when(likeMapper.mapToDto(any(), any(UserDto.class))).thenReturn(new LikeDto());

        var result = likeService.getTweetLikes(tweetId).get();

        verify(likeRepository).findByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET);
        verify(userService).getUserSummary(userId);
        verify(likeMapper).mapToDto(any(LikeEntity.class), any(UserDto.class));
        assertNotNull(result);
    }

    @Test
    void testGetTweetLikesCount() throws ExecutionException, InterruptedException {
        when(likeRepository.countByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET))
            .thenReturn(10L);

        var result = likeService.getTweetLikesCount(tweetId).get();

        verify(likeRepository).countByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET);
        assertEquals(10L, result);
    }

    @Test
    void testLikeTweet() throws ExecutionException, InterruptedException {
        when(tweetService.getTweetSummary(tweetId)).thenReturn(new TweetDto());
        when(likeMapper.mapRequestToEntity(userId, tweetId, LikeEntity.LikeableType.TWEET))
            .thenReturn(new LikeEntity());
        when(likeRepository.save(any(LikeEntity.class))).thenReturn(new LikeEntity());
        when(likeMapper.mapEntityToDto(any(LikeEntity.class))).thenReturn(new LikeDto());

        var result = likeService.likeTweet(userId, tweetId).get();

        verify(tweetService).getTweetSummary(tweetId);
        verify(likeRepository).save(any(LikeEntity.class));
        verify(likeMapper).mapEntityToDto(any(LikeEntity.class));
        assertNotNull(result);
    }

    @Test
    void testUnlikeTweet() throws ExecutionException, InterruptedException {
        likeService.unlikeTweet(userId, tweetId).get();

        verify(likeRepository).deleteByUserIdAndLikeableIdAndLikeableType(
            userId, tweetId, LikeEntity.LikeableType.TWEET);
    }

    @Test
    void testLikeReply() throws ExecutionException, InterruptedException {
        when(replyRepository.findByIdAndUserId(replyId, userId)).thenReturn(Optional.of(new ReplyEntity()));
        when(likeMapper.mapRequestToEntity(userId, replyId, LikeEntity.LikeableType.REPLY))
            .thenReturn(new LikeEntity());
        when(likeRepository.save(any(LikeEntity.class))).thenReturn(new LikeEntity());
        when(likeMapper.mapEntityToDto(any(LikeEntity.class))).thenReturn(new LikeDto());

        var result = likeService.likeReply(userId, replyId).get();

        verify(replyRepository).findByIdAndUserId(replyId, userId);
        verify(likeRepository).save(any(LikeEntity.class));
        verify(likeMapper).mapEntityToDto(any(LikeEntity.class));
        assertNotNull(result);
    }

    @Test
    void testUnlikeReply() throws ExecutionException, InterruptedException {
        likeService.unlikeReply(userId, replyId).get();

        verify(likeRepository).deleteByUserIdAndLikeableIdAndLikeableType(
            userId, replyId, LikeEntity.LikeableType.REPLY);
    }
}
