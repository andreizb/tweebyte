package ro.tweebyte.tweetservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TweetService {

    private static final int MAX_RETRIES = 10;
    private static final String FOLLOWED_CACHE = "followed_cache";

    private final TweetRepository tweetRepository;
    private final TweetMapper tweetMapper;
    private final UserService userService;
    private final MentionService mentionService;
    private final HashtagService hashtagService;
    private final InteractionClient interactionClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @Qualifier(value = "executorService")
    private final ExecutorService executorService;

    public CompletableFuture<List<TweetDto>> getUserFeed(UUID userId, String authToken) {
        return interactionClient.getFollowedIds(userId)
            .exceptionally(v -> getFollowedUsersFromCache(userId))
            .thenApply(tweetRepository::findByUserIdInOrderByCreatedAtDesc)
            .thenCompose(tweetEntities -> getTweetsPage(tweetEntities, authToken));
    }

    @SneakyThrows
    private List<UUID> getFollowedUsersFromCache(UUID userId) {
        try {
            return objectMapper.readValue(
                redisTemplate.opsForValue().get(FOLLOWED_CACHE + "::" + userId),
                new TypeReference<List<UUID>>() {}
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }


    public CompletableFuture<List<TweetDto>> getUserTweets(UUID userId, String authToken) {
        return CompletableFuture.supplyAsync(() ->
            tweetRepository.findByUserId(userId)
        ).thenCompose(tweetEntities -> getTweetsPage(tweetEntities, authToken));
    }

    private CompletableFuture<List<TweetDto>> getTweetsPage(List<TweetEntity> tweetEntitiesPage, String authToken) {
        List<UUID> tweetIds = tweetEntitiesPage.stream().map(TweetEntity::getId).collect(Collectors.toList());

        return interactionClient.getTweetSummaries(tweetIds, authToken)
            .thenApply(tweetSummaryDtos -> tweetSummaryDtos.stream().collect(Collectors.toMap(TweetSummaryDto::getTweetId, Function.identity())))
            .thenApply(tweetsMap ->
                tweetEntitiesPage
                    .stream()
                    .map(tweetEntity -> tweetMapper.mapEntityToDto(
                        tweetEntity,
                        tweetsMap.get(tweetEntity.getId()).getLikesCount(),
                        tweetsMap.get(tweetEntity.getId()).getRetweetsCount(),
                        tweetsMap.get(tweetEntity.getId()).getRepliesCount(),
                        tweetsMap.get(tweetEntity.getId()).getTopReply()
                    )
            ).collect(Collectors.toList()));
    }

    public CompletableFuture<TweetDto> getTweet(UUID tweetId, String authToken) {
        CompletableFuture<TweetEntity> tweetEntityFuture = CompletableFuture.supplyAsync(() ->
            tweetRepository.findById(tweetId).orElseThrow(() -> new TweetNotFoundException("Tweet not found for id: " + tweetId))
        );

        CompletableFuture<Long> likesFuture = tweetEntityFuture.thenComposeAsync(tweetEntity -> interactionClient.getLikesCount(tweetEntity.getId(), authToken));
        CompletableFuture<Long> repliesCountFuture = tweetEntityFuture.thenComposeAsync(tweetEntity -> interactionClient.getRepliesCount(tweetEntity.getId(), authToken));
        CompletableFuture<Long> retweetsFuture = tweetEntityFuture.thenComposeAsync(tweetEntity -> interactionClient.getRetweetsCount(tweetEntity.getId(), authToken));
        CompletableFuture<List<ReplyDto>> repliesFuture = tweetEntityFuture.thenComposeAsync(tweetEntity -> interactionClient.getRepliesForTweet(tweetEntity.getId(), authToken));

        return CompletableFuture.allOf(likesFuture, repliesCountFuture, retweetsFuture, repliesFuture)
            .thenApply(voided -> {
                Long likes = likesFuture.join();
                Long repliesCount = repliesCountFuture.join();
                Long retweets = retweetsFuture.join();
                List<ReplyDto> replies = repliesFuture.join();
                TweetEntity tweetEntity = tweetEntityFuture.join();

                return tweetMapper.mapEntityToDto(
                    tweetEntity,
                    likes,
                    repliesCount,
                    retweets,
                    replies
                );
            })
            .exceptionally(ex -> {
                throw new TweetException("Failed to fetch tweet details");
            });
    }

    public CompletableFuture<TweetDto> getTweetSummary(UUID tweetId) {
        return CompletableFuture.supplyAsync(() -> tweetRepository.findById(tweetId)
                .orElseThrow(() -> new TweetNotFoundException("Tweet not found for id: " + tweetId))
            ).thenApply(tweetMapper::mapEntityToDto);
    }

    public CompletableFuture<List<TweetDto>> getUserTweetsSummary(UUID userId) {
        return CompletableFuture.supplyAsync(() -> tweetRepository.findByUserId(userId))
            .thenApply(tweetEntities -> tweetEntities.stream().map(tweetMapper::mapEntityToDto).collect(Collectors.toList()));
    }

    public CompletableFuture<TweetDto> createTweet(TweetCreationRequest request) {
        return CompletableFuture.supplyAsync(
                () -> tweetRepository.save(tweetMapper.mapCreationRequestToEntity(request)), executorService
            )
            .thenApply(tweetEntity -> {
                request.setId(tweetEntity.getId());
                return tweetEntity;
            })
            .thenApplyAsync(tweetEntity -> {
                CompletableFuture.runAsync(() -> processTweetTokens(request, mentionService::handleTweetCreationMentions), executorService);
                CompletableFuture.runAsync(() -> processTweetTokens(request, hashtagService::handleTweetCreationHashtags), executorService);
                return tweetEntity;
            }, executorService)
            .thenApplyAsync(tweetMapper::mapEntityToCreationDto, executorService);
    }

    public CompletableFuture<Void> updateTweet(TweetUpdateRequest request) {
        return CompletableFuture.supplyAsync(
                () -> tweetRepository.save(tweetMapper.mapUpdateRequestToEntity(request,tweetRepository
                    .findByIdAndUserId(request.getId(), request.getUserId())
                    .orElseThrow(() -> new TweetNotFoundException("Tweet not found for id " + request.getId())
            ))))
            .thenAcceptAsync(tweetEntity -> tweetRepository.save(tweetMapper.mapUpdateRequestToEntity(request, tweetEntity)))
            .thenAcceptAsync(tweetEntity -> {
                CompletableFuture.runAsync(() -> processTweetTokens(request, mentionService::handleTweetCreationMentions), executorService);
                CompletableFuture.runAsync(() -> processTweetTokens(request, hashtagService::handleTweetCreationHashtags), executorService);
            }, executorService);
    }

    public CompletableFuture<Void> deleteTweet(UUID tweetId) {
        return CompletableFuture.runAsync(
            () -> tweetRepository.deleteById(tweetId)
        );
    }

    public CompletableFuture<List<TweetDto>> searchTweets(String searchTerm) {
        return CompletableFuture.supplyAsync(() ->
            tweetRepository.findBySimilarity('%' + searchTerm + '%')
        ).thenCompose(this::computeTweetsFromPage);
    }

    public CompletableFuture<List<TweetDto>> searchTweetsByHashtag(String searchTerm) {
        return CompletableFuture.supplyAsync(() ->
            tweetRepository.findByHashtag(searchTerm)
        ).thenCompose(this::computeTweetsFromPage);
    }

    private CompletionStage<List<TweetDto>> computeTweetsFromPage(List<TweetEntity> page) {
        List<CompletableFuture<TweetDto>> futures = page.stream().map(tweetEntity ->
            CompletableFuture.supplyAsync(() -> userService.getUserSummary(tweetEntity.getUserId()))
                .thenApply(userSummary -> tweetMapper.mapEntityToDto(tweetEntity, userSummary))
        ).toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    private void processTweetTokens(TweetRequest request, Consumer<TweetRequest> consumer) {
        int attempt = 0;
        boolean success = false;

        while (attempt < MAX_RETRIES && !success) {
            try {
                consumer.accept(request);
                success = true;
            } catch (TweetNotFoundException e) {
                break;
            } catch (Exception ignored) {
            } finally {
                attempt++;
            }
        }

        if (!success && attempt >= MAX_RETRIES) {
            throw new TweetException("Tokenization failed for tweet: " + request);
        }
    }

}
