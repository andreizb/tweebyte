package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.FollowNotFoundException;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final String FOLLOWED_CACHE = "followed_cache";
    private static final String FOLLOWING_CACHE = "following_cache";

    private final UserService userService;

    private final FollowRepository followRepository;

    private final FollowMapper followMapper;

    private final CacheManager cacheManager;

    private final ObjectMapper objectMapper;

    public CompletableFuture<List<FollowDto>> getFollowers(UUID userId) {
//        return CompletableFuture.supplyAsync(() ->
//            followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, FollowEntity.Status.ACCEPTED)
//        ).thenCompose(followEntities -> {
//            List<CompletableFuture<FollowDto>> futures = followEntities.stream().map(followEntity ->
//                CompletableFuture.supplyAsync(() -> userService.getUserSummary(followEntity.getFollowerId()))
//                    .thenApply(userSummary -> followMapper.mapEntityToDto(followEntity, userSummary.getUserName()))
//            ).collect(Collectors.toList());
//
//            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//                .thenApply(v -> getFollowsPage(futures, FOLLOWERS_CACHE, userId));
//        });
        return CompletableFuture.supplyAsync(() -> followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, FollowEntity.Status.ACCEPTED))
            .thenApply(futures -> futures.stream().map(future -> followMapper.mapEntityToDto(future, "some user")).collect(Collectors.toList()));
    }

    public CompletableFuture<List<FollowDto>> getFollowing(UUID userId) {
//        return CompletableFuture.supplyAsync(() ->
//            followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, FollowEntity.Status.ACCEPTED)
//        ).thenCompose(followEntities -> {
//            List<CompletableFuture<FollowDto>> futures = followEntities.stream().map(followEntity ->
//                CompletableFuture.supplyAsync(() -> userService.getUserSummary(followEntity.getFollowedId()))
//                    .thenApply(userSummary -> followMapper.mapEntityToDto(followEntity, userSummary.getUserName()))
//            ).collect(Collectors.toList());
//
//            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//                .thenApply(v -> getFollowsPage(futures, FOLLOWING_CACHE, userId));
//        });
        return CompletableFuture.supplyAsync(() -> followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
            .thenApply(futures -> futures.stream().map(future -> followMapper.mapEntityToDto(future, "some user")).collect(Collectors.toList()));
    }

    public CompletableFuture<Long> getFollowersCount(UUID userId) {
//        CompletableFuture<Long> countFromCache = CompletableFuture.supplyAsync(
//                () -> Objects.requireNonNull(cacheManager.getCache(FOLLOWERS_CACHE)).get(userId, String.class)
//        ).thenApply(v -> {
//            try {
//                return Long.valueOf(objectMapper.readValue(v, new TypeReference<List<FollowDto>>() {}).size());
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
//        });

        CompletableFuture<Long> countFromRepo = CompletableFuture.supplyAsync(() ->
            followRepository.countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED)
        );

//        return CompletableFuture.anyOf(countFromCache, countFromRepo)
//                .thenApply(result -> (Long) result);
        return countFromRepo;
    }

    public CompletableFuture<Long> getFollowingCount(UUID userId) {
        return CompletableFuture.supplyAsync(() ->
            followRepository.countByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED)
        );
    }

    public CompletableFuture<List<FollowDto>> getFollowRequests(UUID userId) {
        return CompletableFuture.supplyAsync(() ->
            followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, FollowEntity.Status.PENDING)
        ).thenApply(followEntities -> followEntities.stream().map(followMapper::mapEntityToDto).collect(Collectors.toList()));
    }

    public CompletableFuture<FollowDto> follow(UUID userId, UUID followedId) {
        return CompletableFuture.supplyAsync(() -> Boolean.TRUE)
            .thenApply(isPrivate ->
                followMapper.mapRequestToEntity(
                    userId, followedId, isPrivate ? FollowEntity.Status.PENDING : FollowEntity.Status.ACCEPTED
                )
            )
            .thenApply(followRepository::save)
            .thenApply(followMapper::mapEntityToDto);
    }

    @CacheEvict(value = "follow_recommendations", key = "#userId")
    public CompletableFuture<Void> updateFollowRequest(UUID userId, UUID followRequestId, FollowEntity.Status status) {
        return CompletableFuture.supplyAsync(() ->
            followRepository.findById(followRequestId).orElseThrow(() -> new FollowNotFoundException("Follow request not found for id " + followRequestId))
        ).thenAccept(followEntity -> {
            if (status == FollowEntity.Status.PENDING || followEntity.getFollowerId().equals(userId) && status == FollowEntity.Status.ACCEPTED) {
                throw new RuntimeException();
            }
            followEntity.setStatus(status);
            followRepository.save(followEntity);
        });
    }

    public CompletableFuture<List<UUID>> getFollowedIdentifiers(UUID userId) {
        return CompletableFuture.supplyAsync(() -> followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
            .thenApply(followEntities -> followEntities.stream().map(FollowEntity::getFollowedId).collect(Collectors.toList()));
    }

    @Transactional
    @Async(value = "executorService")
    @CacheEvict(value = "follow_recommendations", key = "#followerId")
    public CompletableFuture<Void> unfollow(UUID followerId, UUID followedId) {
        followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);
        return CompletableFuture.completedFuture(null);
    }

    @SneakyThrows
    private List<FollowDto> getFollowsPage(List<CompletableFuture<FollowDto>> futures,
                                           String cacheName,
                                           UUID userId) {

        List<FollowDto> follows = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(userId, objectMapper.writeValueAsString(follows));
        }

        return follows;
    }

}
