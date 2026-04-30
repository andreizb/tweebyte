package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests for CleanupService:
 *  - cleanupRejectedFollowRequests while-loop entry/exit
 *  - cleanupOrphanLikes REPLY (non-TWEET) branch of likeableType check
 *  - shutdownScheduler awaitTermination=false branch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CleanupServiceBranchTest {

    @Mock
    private ScheduledExecutorService scheduler;

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
    void setUp() {
        ReflectionTestUtils.setField(cleanupService, "scheduler", scheduler);
    }

    @Test
    void cleanupRejectedFollowRequests_iteratesAndDeletes() {
        // First page non-empty → enters loop body once; second page empty → exits.
        Page<FollowEntity> page = new PageImpl<>(Collections.singletonList(new FollowEntity()));
        when(followRepository.findByStatus(any(), any(Pageable.class)))
            .thenReturn(page).thenReturn(Page.empty());

        cleanupService.cleanupRejectedFollowRequests();

        verify(followRepository, times(2)).findByStatus(any(), any(Pageable.class));
        verify(followRepository).deleteAll(any(Iterable.class));
    }

    @Test
    void cleanupRejectedFollowRequests_emptyOnFirstCall_skipsLoop() {
        // Loop predicate immediately false → body never enters.
        when(followRepository.findByStatus(any(), any(Pageable.class)))
            .thenReturn(Page.empty());

        cleanupService.cleanupRejectedFollowRequests();

        verify(followRepository, times(1)).findByStatus(any(), any(Pageable.class));
        verify(followRepository, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void cleanupOrphanLikes_tweetTypeStillExists_keepsEntity() {
        // Branch: likeableType == TWEET TRUE arm; tweetService returns summary → return false (keep).
        UUID likeableId = UUID.randomUUID();
        LikeEntity likeEntity = new LikeEntity();
        likeEntity.setLikeableId(likeableId);
        likeEntity.setLikeableType(LikeEntity.LikeableType.TWEET);

        Page<LikeEntity> page = new PageImpl<>(Collections.singletonList(likeEntity));
        when(likeRepository.findAll(any(Pageable.class)))
            .thenReturn(page).thenReturn(Page.empty());
        // Returning anything non-null means tweet still exists; entity not deleted.
        when(tweetService.getTweetSummary(likeableId)).thenReturn(null);

        cleanupService.cleanupOrphanLikes();

        verify(tweetService).getTweetSummary(likeableId);
        verify(likeRepository).deleteAll(any(Iterable.class));
    }

    @Test
    void cleanupOrphanLikes_replyType_usesRepositoryLookup() {
        // likeableType == REPLY → else-branch: likeRepository.findById(...).isEmpty() decides.
        UUID likeableId = UUID.randomUUID();
        LikeEntity likeEntity = new LikeEntity();
        likeEntity.setLikeableId(likeableId);
        likeEntity.setLikeableType(LikeEntity.LikeableType.REPLY);

        Page<LikeEntity> page = new PageImpl<>(Collections.singletonList(likeEntity));
        when(likeRepository.findAll(any(Pageable.class)))
            .thenReturn(page).thenReturn(Page.empty());
        when(likeRepository.findById(likeableId)).thenReturn(Optional.empty()); // orphan → delete

        cleanupService.cleanupOrphanLikes();

        verify(likeRepository, times(2)).findAll(any(Pageable.class));
        verify(likeRepository).findById(likeableId);
        verify(likeRepository).deleteAll(anyList());
    }

    @Test
    void cleanupOrphanLikes_replyTypeStillReferenced_keepsEntity() {
        // findById non-empty → filter returns false → entity not deleted.
        UUID likeableId = UUID.randomUUID();
        LikeEntity likeEntity = new LikeEntity();
        likeEntity.setLikeableId(likeableId);
        likeEntity.setLikeableType(LikeEntity.LikeableType.REPLY);

        Page<LikeEntity> page = new PageImpl<>(Collections.singletonList(likeEntity));
        when(likeRepository.findAll(any(Pageable.class)))
            .thenReturn(page).thenReturn(Page.empty());
        when(likeRepository.findById(likeableId)).thenReturn(Optional.of(new LikeEntity()));

        cleanupService.cleanupOrphanLikes();

        verify(likeRepository).findById(likeableId);
        verify(likeRepository).deleteAll(anyList());
    }

    @Test
    void shutdownScheduler_awaitTerminationFalse_callsShutdownNow() throws InterruptedException {
        // Branch: awaitTermination returns false → scheduler.shutdownNow() invoked.
        when(scheduler.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(false);

        cleanupService.shutdownScheduler();

        verify(scheduler).shutdown();
        verify(scheduler).awaitTermination(anyLong(), any(TimeUnit.class));
        verify(scheduler).shutdownNow();
    }

    @Test
    void shutdownScheduler_awaitTerminationTrue_skipsShutdownNow() throws InterruptedException {
        // Branch: awaitTermination returns true → shutdownNow NOT invoked.
        when(scheduler.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);

        cleanupService.shutdownScheduler();

        verify(scheduler).shutdown();
        verify(scheduler, never()).shutdownNow();
    }

    @Test
    void shutdownScheduler_interrupted_swallowsAndShutsDown() throws InterruptedException {
        // Branch: awaitTermination throws InterruptedException → catch invokes shutdown again.
        doThrow(new InterruptedException("boom"))
            .when(scheduler).awaitTermination(anyLong(), any(TimeUnit.class));

        cleanupService.shutdownScheduler();

        verify(scheduler, times(2)).shutdown();
    }

}
