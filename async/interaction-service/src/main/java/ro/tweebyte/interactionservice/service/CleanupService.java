package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleanupService {

    private static final int BATCH_SIZE = 100;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final TweetService tweetService;

    private final FollowRepository followRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final RetweetRepository retweetRepository;

    /**
     * Cleanup tasks fire with a 24-hour INITIAL DELAY so the orphan/cleanup
     * sweeps don't run during a single benchmark or dev session (typically
     * minutes, not days), keeping latency / GC numbers in testing/RESULTS.md
     * §3 + §5 free of cleanup noise. Once the application has been up for
     * 24h (production-like), the schedules tick at their configured periods.
     * The reactive stack's CleanupService.startCleanupTasks uses the same
     * 24h initial delay via Flux.interval — same observable behaviour on
     * both stacks.
     */
    @PostConstruct
    public void startCleanupTasks() {
        scheduler.scheduleAtFixedRate(this::cleanupRejectedFollowRequests, 24, 3, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(this::cleanupOrphanLikes,            24, 2, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(this::cleanupOrphanReplies,          24, 1, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(this::cleanupOrphanRetweets,         24, 1, TimeUnit.HOURS);
    }

    @Transactional
    public void cleanupRejectedFollowRequests() {
        int offset = 0;

        Page<FollowEntity> followEntities;

        while (!(followEntities = followRepository.findByStatus(FollowEntity.Status.REJECTED, PageRequest.of(offset, BATCH_SIZE))).isEmpty()) {
            followRepository.deleteAll(followEntities);
            offset++;
        }
    }

    @Transactional
    public void cleanupOrphanLikes() {
        int offset = 0;

        Page<LikeEntity> likeEntities;

        while (!(likeEntities = likeRepository.findAll(PageRequest.of(offset, BATCH_SIZE))).isEmpty()) {
            List<LikeEntity> deletableLikes = likeEntities
                .getContent()
                .stream()
                .filter(likeEntity -> {
                    if (likeEntity.getLikeableType() == LikeEntity.LikeableType.TWEET) {
                        try {
                            tweetService.getTweetSummary(likeEntity.getLikeableId());
                            return false;
                        } catch (TweetNotFoundException e) {
                            return true;
                        }
                    } else {
                        return likeRepository.findById(likeEntity.getLikeableId()).isEmpty();
                    }
                })
                .collect(Collectors.toList());

            likeRepository.deleteAll(deletableLikes);
            offset++;
        }
    }

    @Transactional
    public void cleanupOrphanReplies() {
        int offset = 0;

        Page<ReplyEntity> replyEntities;

        while (!(replyEntities = replyRepository.findAll(PageRequest.of(offset, BATCH_SIZE))).isEmpty()) {
            List<ReplyEntity> deletableReplies = replyEntities
                .getContent()
                .stream()
                .filter(retweetEntity -> {
                    try {
                        tweetService.getTweetSummary(retweetEntity.getTweetId());
                        return false;
                    } catch (TweetNotFoundException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

            replyRepository.deleteAll(deletableReplies);

            offset++;
        }
    }

    @Transactional
    public void cleanupOrphanRetweets() {
        int offset = 0;

        Page<RetweetEntity> retweetEntities;

        while (!(retweetEntities = retweetRepository.findAll(PageRequest.of(offset, BATCH_SIZE))).isEmpty()) {
            List<RetweetEntity> deletableRetweets = retweetEntities
                .getContent()
                .stream()
                .filter(retweetEntity -> {
                    try {
                        tweetService.getTweetSummary(retweetEntity.getOriginalTweetId());
                        return false;
                    } catch (TweetNotFoundException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

            retweetRepository.deleteAll(deletableRetweets);

            offset++;
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdown();
        }
    }

}
