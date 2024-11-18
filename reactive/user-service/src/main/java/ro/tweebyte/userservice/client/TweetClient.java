package ro.tweebyte.userservice.client;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import ro.tweebyte.userservice.model.TweetDto;

import java.util.UUID;

@Component
public class TweetClient {

    @Value("${app.tweet.base-url}")
    private String baseUrl;

    private WebClient webClient = null;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Flux<TweetDto> getUserTweets(UUID userId, String authToken) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/tweets/user/{userId}").build(userId.toString()))
            .header("Authorization", authToken)
            .retrieve()
            .bodyToFlux(TweetDto.class);
    }

}
