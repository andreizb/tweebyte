package ro.tweebyte.tweetservice.client;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;

import java.util.UUID;

@Component
public class UserClient {

    @Value("${app.user.base-url}")
    private String baseUrl;

    private WebClient webClient = null;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<UserDto> getUserSummary(String userName) {
        return webClient.get().uri("/users/summary/name/{userName}", userName)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new UserNotFoundException("User not found for name: " + userName)))
            .bodyToMono(UserDto.class);
    }

    public Mono<UserDto> getUserSummary(UUID userId) {
        return webClient.get().uri("/users/summary/{userId}", userId)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new UserNotFoundException("User not found for id: " + userId)))
            .bodyToMono(UserDto.class);
    }

}
