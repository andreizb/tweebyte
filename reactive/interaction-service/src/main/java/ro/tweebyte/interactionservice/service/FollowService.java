package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.FollowNotFoundException;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final String FOLLOWING_CACHE = "following_cache:";

    private final UserService userService;

    private final FollowRepository followRepository;

    private final FollowMapper followMapper;

    private final CacheManager cacheManager;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Flux<FollowDto> getFollowers(UUID userId, String authToken) {
        return followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, Status.ACCEPTED.name())
            .flatMap(followEntity -> userService.getUserSummary(followEntity.getFollowerId())
                .map(userSummary -> followMapper.mapEntityToDto(followEntity, userSummary.getUserName())));
    }

    public Flux<FollowDto> getFollowing(UUID userId, String authToken) {
        String key = FOLLOWING_CACHE + ":" + userId;
        return redisTemplate.opsForList().range(key, 0, -1)
                .cast(FollowDto.class)
                .switchIfEmpty(fetchFollowing(userId, authToken)
                        .collectList()
                        .doOnNext(followDtoList -> redisTemplate.opsForList().rightPushAll(key, followDtoList))
                        .flatMapMany(Flux::fromIterable));
    }

    public Mono<Long> getFollowersCount(UUID userId) {
        return getFollowersCountFromRepo(userId);
    }

    @CircuitBreaker(name = "followersCountCircuitBreaker", fallbackMethod = "getFollowersCountFromCache")
    public Mono<Long> getFollowersCountFromRepo(UUID userId) {
        return followRepository.countByFollowedIdAndStatus(userId, Status.ACCEPTED.name());
    }

    public Mono<Long> getFollowersCountFromCache(UUID userId) {
        String key = FOLLOWING_CACHE + userId;
        return redisTemplate.opsForValue().get(key)
                .cast(String.class)
                .map(v -> {
                    try {
                        return Long.valueOf(objectMapper.readValue(v, new TypeReference<List<FollowDto>>() {}).size());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public Mono<Long> getFollowingCount(UUID userId) {
        return followRepository.countByFollowerIdAndStatus(userId, Status.ACCEPTED.name());
    }

    public Flux<FollowDto> getFollowRequests(UUID userId) {
        return followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, Status.PENDING.name())
            .map(followMapper::mapEntityToDto);
    }

    public Flux<UUID> getFollowedIdentifiers(UUID userId) {
        return followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name())
            .map(FollowEntity::getFollowedId);
    }

    public Mono<FollowDto> follow(UUID userId, UUID followedId) {
//        return userService.getUserSummary(followedId)
        return Mono.fromSupplier(() -> UserDto.builder().isPrivate(true).build())
            .map(userSummary -> followMapper.mapRequestToEntity(userId, followedId, userSummary.getIsPrivate() ? Status.PENDING.name() : Status.ACCEPTED.name()))
            .flatMap(followRepository::save)
            .map(followMapper::mapEntityToDto);
//            .doOnSuccess(dto -> Objects.requireNonNull(cacheManager.getCache("follow_recommendations")).evict(userId.toString()));
    }

    public Mono<Void> updateFollowRequest(UUID userId, UUID followRequestId, Status status) {
        return followRepository.findById(followRequestId)
            .switchIfEmpty(Mono.error(new FollowNotFoundException("Follow request not found for id " + followRequestId)))
            .flatMap(followEntity -> {
                if (status == Status.PENDING || followEntity.getFollowerId().equals(userId) && status == Status.ACCEPTED) {
                    return Mono.error(new RuntimeException("Invalid status update"));
                }
                followEntity.setStatus(status.name());
                return followRepository.save(followEntity);
            })
            .doFinally(signalType -> Objects.requireNonNull(cacheManager.getCache("follow_recommendations")).evict(userId.toString()))
            .then();
    }

    public Mono<Void> unfollow(UUID followerId, UUID followedId) {
        return followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId)
            .doFinally(signalType -> Objects.requireNonNull(cacheManager.getCache("follow_recommendations")).evict(followerId.toString()));
    }

    private Flux<FollowDto> fetchFollowing(UUID userId, String authToken) {
        return followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, Status.ACCEPTED.name())
                .flatMap(followEntity -> userService.getUserSummary(followEntity.getFollowedId())
                        .map(userSummary -> followMapper.mapEntityToDto(followEntity, userSummary.getUserName())));
    }

}
