package ro.tweebyte.interactionservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.LikeableType;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CleanupService {

    private final TweetService tweetService;

    private final FollowRepository followRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final RetweetRepository retweetRepository;

    @Lazy
    @Resource(name = "cleanupService")
    private CleanupService self;

    private final Scheduler scheduler = Schedulers.newBoundedElastic(5, 10, "CleanupService");

//    @PostConstruct
//    public void startCleanupTasks() {
//        Flux.interval(Duration.ZERO, Duration.ofHours(3), scheduler)
//            .flatMap(tick -> self.cleanupRejectedFollowRequests())
//            .subscribe();
//
//        Flux.interval(Duration.ZERO, Duration.ofHours(2), scheduler)
//            .flatMap(tick -> self.cleanupOrphanLikes())
//            .subscribe();
//
//        Flux.interval(Duration.ZERO, Duration.ofHours(1), scheduler)
//            .flatMap(tick -> self.cleanupOrphanReplies())
//            .subscribe();
//
//        Flux.interval(Duration.ZERO, Duration.ofHours(1), scheduler)
//            .flatMap(tick -> self.cleanupOrphanRetweets())
//            .subscribe();
//    }

    @Transactional
    public Mono<Void> cleanupRejectedFollowRequests() {
        return followRepository.findByStatus(Status.REJECTED.name())
            .buffer(100)
            .flatMap(followRepository::deleteAll)
            .then();
    }

    @Transactional
    public Mono<Void> cleanupOrphanLikes() {
        return likeRepository.findAll()
            .buffer(100)
            .flatMap(likes -> Flux.fromIterable(likes)
                .filterWhen(likeEntity -> {
                    if (likeEntity.getLikeableType().equals(LikeableType.TWEET.name())) {
                        return tweetService.getTweetSummary(likeEntity.getLikeableId())
                            .thenReturn(false)
                            .onErrorResume(TweetNotFoundException.class, e -> Mono.just(true));
                    } else {
                        return likeRepository.existsById(likeEntity.getLikeableId()).map(exists -> !exists);
                    }
                })
                .collectList())
            .flatMap(likeRepository::deleteAll)
            .then();
    }

    @Transactional
    public Mono<Void> cleanupOrphanReplies() {
        return replyRepository.findAll()
            .buffer(100)
            .flatMap(replies -> Flux.fromIterable(replies)
                .filterWhen(replyEntity -> tweetService.getTweetSummary(replyEntity.getTweetId())
                    .thenReturn(false)
                    .onErrorResume(TweetNotFoundException.class, e -> Mono.just(true)))
                .collectList())
            .flatMap(replyRepository::deleteAll)
            .then();
    }

    @Transactional
    public Mono<Void> cleanupOrphanRetweets() {
        return retweetRepository.findAll()
            .buffer(100)
            .flatMap(retweets -> Flux.fromIterable(retweets)
                .filterWhen(retweetEntity -> tweetService.getTweetSummary(retweetEntity.getOriginalTweetId())
                    .thenReturn(false)
                    .onErrorResume(TweetNotFoundException.class, e -> Mono.just(true)))
                .collectList())
            .flatMap(retweetRepository::deleteAll)
            .then();
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.dispose();
    }

}
