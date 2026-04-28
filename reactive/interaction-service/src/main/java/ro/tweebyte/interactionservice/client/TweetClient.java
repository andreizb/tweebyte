package ro.tweebyte.interactionservice.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TweetClient {

    @Value("${app.tweet.base-url}")
    private String baseUrl;

    private WebClient webClient = null;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<TweetDto> getTweetSummary(UUID tweetId) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("tweets/{tweetId}/summary").build(tweetId))
            .retrieve()
            .bodyToMono(TweetDto.class)
            .onErrorMap(WebClientResponseException.class, e -> {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return new TweetNotFoundException("Tweet not found with id: " + tweetId);
                }
                return new InteractionException(e);
            });
    }

    public Flux<TweetDto> getUserTweetsSummary(UUID userId) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("tweets/user/{userId}/summary").build(userId))
            .retrieve()
            .bodyToFlux(TweetDto.class)
            .onErrorMap(WebClientResponseException.class, e -> {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return new TweetNotFoundException("Tweets not found for user id: " + userId);
                }
                return new InteractionException(e);
            });
    }

    public Flux<TweetDto.HashtagDto> getPopularHashtags() {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("tweets/hashtags/popular").build())
            .retrieve()
            .bodyToFlux(TweetDto.HashtagDto.class)
            .onErrorMap(InteractionException::new);
    }

}
