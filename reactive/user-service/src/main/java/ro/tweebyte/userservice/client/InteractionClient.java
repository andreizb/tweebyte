package ro.tweebyte.userservice.client;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.exception.FollowRetrievingException;

import java.util.UUID;

@Component
public class InteractionClient {

    @Value("${app.interaction.base-url}")
    private String baseUrl;

    private WebClient webClient = null;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<Long> getFollowersCount(UUID userId, String authToken) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/follows/{userId}/followers/count").build(userId.toString()))
            .header("Authorization", authToken)
            .retrieve()
            .bodyToMono(Long.class)
            .onErrorMap(e -> new FollowRetrievingException());
    }

    public Mono<Long> getFollowingCount(UUID userId, String authToken) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/follows/{userId}/following/count").build(userId.toString()))
            .header("Authorization", authToken)
            .retrieve()
            .bodyToMono(Long.class)
            .onErrorMap(e -> new FollowRetrievingException());
    }

}
