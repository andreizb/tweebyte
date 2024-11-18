package ro.tweebyte.tweetservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import ro.tweebyte.tweetservice.exception.FollowRetrievingException;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.util.ClientUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Component
@RequiredArgsConstructor
public class InteractionClient {

    private static final String FOLLOWED_CACHE = "followed_cache";

    @Value("${app.interaction.base-url}")
    private String BASE_URL;

    private final ClientUtil clientUtil;

    private final ExecutorService executorService = ForkJoinPool.commonPool();

    private final RedisTemplate<String, Object> redisTemplate;

    private final HttpClient client = HttpClient.newBuilder().executor(executorService).build();

    public CompletableFuture<Long> getRepliesCount(UUID tweetId, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .uri(new URI(BASE_URL + "replies/tweet/" + tweetId + "/count")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, Long.class));
        } catch (Exception e) {
            throw new TweetException(e);
        }
    }

    public CompletableFuture<Long> getLikesCount(UUID tweetId, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .uri(new URI(BASE_URL + "likes/" + tweetId + "/count")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, Long.class));
        } catch (Exception e) {
            throw new TweetException(e);
        }
    }

    public CompletableFuture<Long> getRetweetsCount(UUID tweetId, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .uri(new URI(BASE_URL + "retweets/tweet/" + tweetId + "/count")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, Long.class));
        } catch (Exception e) {
            throw new TweetException(e);
        }
    }

    public CompletableFuture<ReplyDto> getTopReply(UUID tweetId) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "replies/tweet/" + tweetId + "/top")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, ReplyDto.class));
        } catch (Exception e) {
            throw new TweetException(e);
        }
    }

    @SuppressWarnings(value = "unchecked")
    public CompletableFuture<List<ReplyDto>> getRepliesForTweet(UUID tweetId, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .uri(new URI(BASE_URL + "replies/tweet/" + tweetId)).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, new TypeReference<List<ReplyDto>>() {}));
        } catch (Exception e) {
            throw new TweetException(e);
        }
    }

    public CompletableFuture<List<UUID>> getFollowedIds(UUID userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "follows/" + userId + "/followers/identifiers")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        redisTemplate.opsForValue().set(FOLLOWED_CACHE + "::" + userId, response.body());
                    }
                    return response;
                })
                .thenApply(response -> clientUtil.parseResponse(response, new TypeReference<List<UUID>>() {}));
        } catch (Exception e) {
            throw new FollowRetrievingException();
        }
    }

    public CompletableFuture<List<TweetSummaryDto>> getTweetSummaries(List<UUID> tweetIds, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .header("Content-Type", "application/json")
                .uri(new URI(BASE_URL + "recommendations/tweet/summary"))
                .POST(HttpRequest.BodyPublishers.ofString(clientUtil.generateBody(tweetIds)))
                .build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, new TypeReference<List<TweetSummaryDto>>() {}));
        } catch (Exception e) {
            throw new TweetException(e);
        }
    }

}
