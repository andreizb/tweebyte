package ro.tweebyte.tweetservice.client;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;

import java.util.List;
import java.util.UUID;

@Component
public class InteractionClient {

    private static final String FOLLOWED_CACHE = "followed_cache";

    @Value("${app.interaction.base-url}")
    private String baseUrl;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    private WebClient webClient = null;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    public Mono<Long> getRepliesCount(UUID tweetId, String authorization) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/replies/tweet/{tweetId}/count").build(tweetId.toString()))
            .header("Authorization", authorization)
            .retrieve()
            .bodyToMono(Long.class);
    }

    public Mono<Long> getLikesCount(UUID tweetId, String authorization) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/likes/{tweetId}/count").build(tweetId.toString()))
            .header("Authorization", authorization)
            .retrieve()
            .bodyToMono(Long.class);
    }

    public Mono<Long> getRetweetsCount(UUID tweetId, String authorization) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/retweets/tweet/{tweetId}/count").build(tweetId.toString()))
            .header("Authorization", authorization)
            .retrieve()
            .bodyToMono(Long.class);
    }

    public Mono<ReplyDto> getTopReply(UUID tweetId, String authorization) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/replies/tweet/{tweetId}/top").build(tweetId.toString()))
            .header("Authorization", authorization)
            .retrieve()
            .bodyToMono(ReplyDto.class);
    }

    public Flux<ReplyDto> getRepliesForTweet(UUID tweetId, String authorization) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/replies/tweet/{tweetId}").build(tweetId.toString()))
            .header("Authorization", authorization)
            .retrieve().bodyToFlux(ReplyDto.class);
    }

    public Flux<UUID> getFollowedIds(UUID userId, String authorization) {
        String cacheKey = FOLLOWED_CACHE + "::" + userId;
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/follows/{userId}/followers/identifiers").build(userId.toString()))
            .header("Authorization", authorization)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<UUID>>() {})
            .doOnNext(followerIds -> redisTemplate.opsForValue().set(cacheKey, followerIds))
            .flatMapMany(Flux::fromIterable);
    }

    public Flux<TweetSummaryDto> getTweetSummaries(List<UUID> tweetIds, String authorization) {
        return webClient.post()
            .uri(uriBuilder -> uriBuilder.path("/recommendations/tweet/summary").build())
            .header("Authorization", authorization)
            .bodyValue(tweetIds)
            .retrieve()
            .bodyToFlux(TweetSummaryDto.class);
    }

}
