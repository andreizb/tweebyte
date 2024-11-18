package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final CacheManager cacheManager;

    private final UserService userService;
    private final TweetService tweetService;

    private final FollowRepository followRepository;
    private final LikeService likeService;
    private final RetweetService retweetService;
    private final ReplyRepository replyRepository;
    private final LikeRepository likeRepository;
    private final RetweetRepository retweetRepository;

    @Lazy
    @Resource(name = "recommendationService")
    private RecommendationService self;

//    @PostConstruct
//    public void startComputationTasks() {
//        scheduler.scheduleAtFixedRate(self::computePopularUsers, 0, 2, TimeUnit.DAYS);
//        scheduler.scheduleAtFixedRate(self::computePopularHashtags, 0, 7, TimeUnit.HOURS);
//    }

    public CompletableFuture<List<UserDto>> recommendUsersToFollow(UUID userId, Pageable pageable) {
        return CompletableFuture.supplyAsync(() -> self.getUserRecommendations(userId, pageable));
    }

    @Cacheable(value = "follow_recommendations", key = "#userId", unless = "#result.isEmpty()")
    public List<UserDto> getUserRecommendations(UUID userId, Pageable pageable) {
        Set<UUID> recommendations = new HashSet<>();

        Page<FollowEntity> followedEntities = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, FollowEntity.Status.ACCEPTED, pageable);

        Set<UUID> followedIds = followedEntities.stream()
                .map(FollowEntity::getFollowedId)
                .collect(Collectors.toSet());

        followedIds.forEach(followedId -> {
            Page<FollowEntity> followedOfFollowed = followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(followedId, FollowEntity.Status.ACCEPTED, pageable);
            followedOfFollowed.forEach(entity -> {
                if (!followedIds.contains(entity.getFollowedId()) && !userId.equals(entity.getFollowedId())) {
                    recommendations.add(entity.getFollowedId());
                }
            });
        });

        fetchPopularUsers(pageable)
                .forEach(popularId -> {
                    if (!followedIds.contains(popularId)) {
                        recommendations.add(popularId);
                    }
                });

        return recommendations.stream().map(userService::getUserSummary).collect(Collectors.toList());
    }

    @Cacheable(value = "popular_users", key = "'p0'", unless = "#result.isEmpty()")
    @SneakyThrows
    public Map<UUID, Double> computePopularUsers() {
        return CompletableFuture.supplyAsync(followRepository::findAllFollowedIds)
            .thenCompose(userIds -> {
                List<CompletableFuture<AbstractMap.SimpleEntry<UUID, Double>>> scoreFutures = userIds.stream()
                        .map(userId -> calculateUserScore(userId)
                                .thenApply(score -> new AbstractMap.SimpleEntry<>(userId, score)))
                        .toList();

                return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> scoreFutures.stream()
                                .map(CompletableFuture::join)
                                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                                .limit(1000L)
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (e1, e2) -> e1,
                                        LinkedHashMap::new)));
            }).get();
    }

    private CompletableFuture<Double> calculateUserScore(UUID userId) {
        CompletableFuture<List<UUID>> tweetIdsFuture = CompletableFuture.supplyAsync(() -> tweetService.getUserTweetsSummary(userId))
            .thenApplyAsync(tweetSummaries -> tweetSummaries.stream().map(TweetDto::getId).collect(Collectors.toList()));

        CompletableFuture<Long> followersCountFuture = CompletableFuture.supplyAsync(() -> followRepository.countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED));

        CompletableFuture<Long> likesCountFuture = tweetIdsFuture.thenComposeAsync(tweetIds ->
            CompletableFuture.allOf(tweetIds.stream().map(likeService::getTweetLikesCount).toArray(CompletableFuture[]::new))
                    .thenApply(v -> tweetIds.stream().mapToLong(tweetId ->
                        likeService.getTweetLikesCount(tweetId).join()).sum())
        );

        CompletableFuture<Long> retweetsCountFuture = tweetIdsFuture.thenComposeAsync(tweetIds ->
            CompletableFuture.allOf(tweetIds.stream().map(retweetService::getRetweetCountOfTweet).toArray(CompletableFuture[]::new))
                    .thenApply(v -> tweetIds.stream().mapToLong(tweetId ->
                        retweetService.getRetweetCountOfTweet(tweetId).join()).sum())
        );

        return CompletableFuture.allOf(followersCountFuture, likesCountFuture, retweetsCountFuture).thenApply(v -> {
            double followersCount = followersCountFuture.join();
            double likesCount = likesCountFuture.join();
            double retweetsCount = retweetsCountFuture.join();
            return followersCount + likesCount + retweetsCount;
        });
    }


    private Collection<UUID> fetchPopularUsers(Pageable pageable) {
        Map<UUID, Double> popularUsersMap = (Map<UUID, Double>) cacheManager.getCache("popular_users").get("p0", Map.class);

        if (popularUsersMap != null) {
            return popularUsersMap.keySet().stream()
                    .skip((long) pageable.getPageSize() * pageable.getPageNumber())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());
        }

        return self.computePopularUsers().keySet();
    }

    @SneakyThrows
    @Async
    public CompletableFuture<List<TweetDto.HashtagDto>> computePopularHashtags() {
        return CompletableFuture.completedFuture(tweetService.getPopularHashtags());
    }

    public CompletableFuture<List<TweetSummaryDto>> findTweetSummaries(List<UUID> tweetIds) {
        return CompletableFuture.supplyAsync(() -> tweetIds.stream()
            .map(oneTweetId -> TweetSummaryDto
                .builder()
                .tweetId(oneTweetId)
                .likesCount(likeRepository.countByLikeableIdAndLikeableType(oneTweetId, LikeEntity.LikeableType.TWEET))
                .repliesCount(replyRepository.countByTweetId(oneTweetId))
                .retweetsCount(retweetRepository.countByOriginalTweetId(oneTweetId))
//                .topReply(replyRepository.findTopReplyByLikesForTweetId(oneTweetId, PageRequest.of(0, 1)).getContent().get(0))
                .topReply(new ReplyDto())
                .build())
            .collect(Collectors.toList()));
    }

}
