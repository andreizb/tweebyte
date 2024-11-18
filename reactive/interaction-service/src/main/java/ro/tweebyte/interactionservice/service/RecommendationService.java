package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String USER_RECOMMENDATIONS_KEY_PREFIX = "follow_recommendations:";
    private static final String POPULAR_USERS_KEY = "popular_users:";

    private final UserService userService;
    private final TweetService tweetService;
    private final FollowRepository followRepository;
    private final LikeService likeService;
    private final RetweetService retweetService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Lazy
    @Resource(name = "recommendationService")
    private RecommendationService self;

    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.DAYS)
    public void computePopularUsersJob() {
        self.fetchPopularUsers();
    }

    @Scheduled(fixedRate = 7, timeUnit = TimeUnit.DAYS)
    public void computePopularHashtagsJob() {
        self.fetchPopularHashtags().subscribe();
    }

    public Flux<UserDto> recommendUsersToFollow(UUID userId) {
        return self.getUserRecommendations(userId);
    }

    public Flux<UserDto> getUserRecommendations(UUID userId) {
        String key = USER_RECOMMENDATIONS_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, new TypeReference<List<UserDto>>() {});
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .switchIfEmpty(fetchUserRecommendations(userId)
                        .collectList()
                        .doOnNext(userDtoList -> {
                            try {
                                String json = objectMapper.writeValueAsString(userDtoList);
                                redisTemplate.opsForValue().set(key, json).subscribe();
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .flatMapMany(Flux::fromIterable));
    }

    public Map<UUID, Double> fetchPopularUsers() {
        return redisTemplate.opsForValue().get(POPULAR_USERS_KEY)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, new TypeReference<Map<UUID, Double>>() {});
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .switchIfEmpty(computePopularUsers()
                        .doOnNext(popularUsers -> {
                            try {
                                String json = objectMapper.writeValueAsString(popularUsers);
                                redisTemplate.opsForValue().set(POPULAR_USERS_KEY, json).subscribe();
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        })).block();
    }

    public Flux<TweetDto.HashtagDto> fetchPopularHashtags() {
        return tweetService.getPopularHashtags();
    }

    private Flux<UserDto> fetchUserRecommendations(UUID userId) {
        return followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name())
                .collectList()
                .flatMapMany(followedEntities -> {
                    Set<UUID> followedIds = followedEntities.stream()
                            .map(FollowEntity::getFollowedId)
                            .collect(Collectors.toSet());

                    Flux<UUID> recommendationsFlux = Flux.fromIterable(followedIds)
                            .flatMap(followedId -> followRepository.findByFollowerIdAndStatus(followedId, Status.ACCEPTED.name()))
                            .filter(entity -> !followedIds.contains(entity.getFollowedId()) && !userId.equals(entity.getFollowedId()))
                            .map(FollowEntity::getFollowedId)
                            .distinct();

                    Flux<UUID> popularUsersFlux = Flux.fromIterable(fetchPopularUsers()
                            .keySet())
                            .filter(popularId -> !followedIds.contains(popularId));

                    return Flux.concat(recommendationsFlux, popularUsersFlux)
                            .distinct()
                            .flatMap(userService::getUserSummary);
                });
    }

    private Mono<Map<UUID, Double>> computePopularUsers() {
        return followRepository.findAllFollowedIds()
                .flatMap(this::calculateUserScore)
                .collectSortedList(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(sortedList -> sortedList.stream().limit(1000)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new)));
    }

    private Mono<Map.Entry<UUID, Double>> calculateUserScore(UUID userId) {
        Flux<UUID> tweetIds = tweetService.getUserTweetsSummary(userId).map(TweetDto::getId);

        Mono<Long> followersCountMono = followRepository.countByFollowedIdAndStatus(userId, Status.ACCEPTED.name());

        Mono<Long> likesCountMono = tweetIds
                .flatMap(likeService::getTweetLikesCount)
                .reduce(0L, Long::sum);

        Mono<Long> retweetsCountMono = tweetIds
                .flatMap(retweetService::getRetweetCountOfTweet)
                .reduce(0L, Long::sum);

        return Mono.zip(followersCountMono, likesCountMono, retweetsCountMono)
                .map(data -> {
                    long sum = data.getT1() + data.getT2() + data.getT3();
                    return new AbstractMap.SimpleEntry<>(userId, (double) sum);
                });
    }

}
