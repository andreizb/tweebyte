package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CleanupServiceTest {

    @Mock
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @Mock
    private TweetService tweetService;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private RetweetRepository retweetRepository;

    @InjectMocks
    private CleanupService cleanupService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(cleanupService, "scheduler", scheduler);
    }

    @Test
    void testCleanupOrphanLikes() {
        Page<LikeEntity> page = new PageImpl<>(Collections.singletonList(new LikeEntity()));
        when(likeRepository.findAll(any(Pageable.class))).thenReturn(page).thenReturn(Page.empty());
        when(tweetService.getTweetSummary(any())).thenThrow(TweetNotFoundException.class);

        cleanupService.cleanupOrphanLikes();

        verify(likeRepository, times(2)).findAll(any(Pageable.class));
        verify(likeRepository).deleteAll(anyList());
    }

    @Test
    void testCleanupOrphanReplies(){
        Page<ReplyEntity> page = new PageImpl<>(Collections.singletonList(new ReplyEntity()));
        when(replyRepository.findAll(any(Pageable.class))).thenReturn(page).thenReturn(Page.empty());
        when(tweetService.getTweetSummary(any())).thenThrow(TweetNotFoundException.class);

        cleanupService.cleanupOrphanReplies();

        verify(replyRepository, times(2)).findAll(any(Pageable.class));
        verify(replyRepository).deleteAll(anyList());
    }

    @Test
    void testCleanupOrphanRetweets() {
        Page<RetweetEntity> page = new PageImpl<>(Collections.singletonList(new RetweetEntity()));
        when(retweetRepository.findAll(any(Pageable.class))).thenReturn(page).thenReturn(Page.empty());
        when(tweetService.getTweetSummary(any())).thenThrow(TweetNotFoundException.class);

        cleanupService.cleanupOrphanRetweets();

        verify(retweetRepository, times(2)).findAll(any(Pageable.class));
        verify(retweetRepository).deleteAll(anyList());
    }

    @Test
    void testSchedulerShutdown() throws InterruptedException {
        cleanupService.shutdownScheduler();
        verify(scheduler).shutdown();
        verify(scheduler).awaitTermination(anyLong(), any());
    }

}
