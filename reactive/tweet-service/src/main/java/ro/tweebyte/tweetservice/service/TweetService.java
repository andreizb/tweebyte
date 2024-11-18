package ro.tweebyte.tweetservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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
    private final HashtagRepository hashtagRepository;
    private final MentionRepository mentionRepository;
    private final TweetHashtagRepository tweetHashtagRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @CircuitBreaker(name = "followedIdsCircuitBreaker", fallbackMethod = "getUserFeedWithCachedFollowed")
    public Flux<TweetDto> getUserFeed(UUID userId, String authorization) {
        return interactionClient.getFollowedIds(userId, authorization)
            .collectList()
            .flatMapMany(tweetRepository::findByUserIdInOrderByCreatedAtDesc)
            .flatMap(tweetEntity -> enrichTweetDto(tweetEntity, authorization));
    }

    public Flux<TweetDto> getUserFeedWithCachedFollowed(UUID userId, String authorization, Throwable t) {
        String key = FOLLOWED_CACHE + "::" + userId;
        return redisTemplate.opsForValue().get(key)
            .map(v -> {
                try {
                    return objectMapper.readValue(v, new TypeReference<List<UUID>>() {});
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }).flatMapMany(Flux::fromIterable)
            .collectList()
            .flatMapMany(tweetRepository::findByUserIdInOrderByCreatedAtDesc)
            .flatMap(tweetEntity -> enrichTweetDto(tweetEntity, authorization));
    }

    private Mono<TweetDto> enrichTweetDto(TweetEntity tweetEntity, String authorization) {
        Mono<Long> likesMono = interactionClient.getLikesCount(tweetEntity.getId(), authorization);
        Mono<Long> repliesMono = interactionClient.getRepliesCount(tweetEntity.getId(), authorization);
        Mono<Long> retweetsMono = interactionClient.getRetweetsCount(tweetEntity.getId(), authorization);
        Mono<ReplyDto> replyMono = interactionClient.getTopReply(tweetEntity.getId(), authorization);
        Mono<List<HashtagEntity>> hashtagsMono = hashtagRepository.findHashtagsByTweetId(tweetEntity.getId()).collectList();
        Mono<List<MentionEntity>> mentionsMono = mentionRepository.findMentionsByTweetId(tweetEntity.getId()).collectList();

        return Mono.zip(likesMono, repliesMono, retweetsMono, replyMono, hashtagsMono, mentionsMono)
            .map(data -> tweetMapper.mapEntityToDto(tweetEntity, data.getT1(), data.getT2(), data.getT3(), data.getT4(), data.getT5(), data.getT6()));
    }

    private Mono<TweetDto> enrichSingleTweetDto(TweetEntity tweetEntity, String authorization) {
        Mono<Long> likesMono = interactionClient.getLikesCount(tweetEntity.getId(), authorization);
        Mono<Long> repliesMono = interactionClient.getRepliesCount(tweetEntity.getId(), authorization);
        Mono<Long> retweetsMono = interactionClient.getRetweetsCount(tweetEntity.getId(), authorization);
        Mono<List<ReplyDto>> repliesListMono = interactionClient.getRepliesForTweet(tweetEntity.getId(), authorization).collectList();
        Mono<List<HashtagEntity>> hashtagsMono = hashtagRepository.findHashtagsByTweetId(tweetEntity.getId()).collectList();
        Mono<List<MentionEntity>> mentionsMono = mentionRepository.findMentionsByTweetId(tweetEntity.getId()).collectList();

        return Mono.zip(likesMono, repliesMono, retweetsMono, repliesListMono, mentionsMono, hashtagsMono)
            .map(data -> tweetMapper.mapEntityToDto(tweetEntity, data.getT1(), data.getT2(), data.getT3(), data.getT4(), data.getT5(), data.getT6()));
    }

    public Mono<TweetDto> getTweet(UUID tweetId, String authorization) {
        Mono<TweetEntity> tweetEntityMono = tweetRepository.findById(tweetId)
            .switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id: " + tweetId)));

        return tweetEntityMono.flatMap(tweetEntity -> enrichSingleTweetDto(tweetEntity, authorization));
    }

    public Flux<TweetDto> getUserTweetsSummary(UUID userId) {
        return tweetRepository.findByUserId(userId)
            .flatMap(tweetEntity -> Mono.just(tweetMapper.mapEntityToDto(tweetEntity)));
    }

    public Mono<TweetDto> createTweet(TweetCreationRequest request) {
        return Mono.fromCallable(() -> tweetMapper.mapCreationRequestToEntity(request))
            .flatMap(tweetRepository::save)
            .doOnSuccess(tweetEntity -> request.setId(tweetEntity.getId()))
            .flatMap(tweetEntity ->
                processTweetTokens(request, mentionService::handleTweetCreationMentions)
                    .and(processTweetTokens(request, hashtagService::handleTweetCreationHashtags))
                    .thenReturn(tweetEntity)
            ).map(tweetMapper::mapEntityToCreationDto);
    }

    public Mono<Void> updateTweet(TweetUpdateRequest request) {
        return tweetRepository.findByIdAndUserId(request.getId(), request.getUserId())
            .switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id " + request.getId())))
            .flatMap(tweetEntity -> tweetRepository.save(tweetMapper.mapUpdateRequestToEntity(request, tweetEntity)))
            .flatMap(tweetEntity ->
                processTweetTokens(request, mentionService::handleTweetUpdateMentions)
                    .and(processTweetTokens(request, hashtagService::handleTweetUpdateHashtags))
                    .then()
            );
    }

    public Mono<Void> deleteTweet(UUID tweetId) {
        return Mono.when(
            mentionRepository.deleteByTweetId(tweetId), tweetHashtagRepository.deleteByTweetId(tweetId)
        ).then(tweetRepository.deleteById(tweetId));
    }

    public Flux<TweetDto> searchTweets(String searchTerm) {
        return tweetRepository.findBySimilarity(searchTerm)
            .flatMap(this::mapEntityToDto);
    }

    public Flux<TweetDto> searchTweetsByHashtag(String searchTerm) {
        return tweetRepository.findByHashtag(searchTerm)
            .flatMap(this::mapEntityToDto);
    }

    private Mono<TweetDto> mapEntityToDto(TweetEntity tweetEntity) {
        return userService.getUserSummary(tweetEntity.getUserId())
            .map(userSummary -> tweetMapper.mapEntityToDto(tweetEntity, userSummary));
    }

    private Mono<Void> processTweetTokens(TweetRequest request, Function<TweetRequest, Mono<Void>> processor) {
        return processor.apply(request)
            .retryWhen(Retry.fixedDelay(MAX_RETRIES, Duration.ofSeconds(1))
                .filter(throwable -> throwable instanceof TweetNotFoundException))
            .onErrorResume(throwable -> Mono.error(new TweetException("Tokenization failed for tweet: " + request)));
    }


    public Flux<TweetDto> getUserTweets(UUID userId, String authorization) {
        return tweetRepository.findByUserId(userId).flatMap(
            tweetEntity -> enrichTweetDto(tweetEntity, authorization)
        );
    }

    public Mono<TweetDto> getTweetSummary(UUID tweetId) {
        Mono<TweetEntity> tweetEntityMono = tweetRepository.findById(tweetId)
            .switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id: " + tweetId)));

        return tweetEntityMono.map(tweetMapper::mapEntityToDto);
    }

}
