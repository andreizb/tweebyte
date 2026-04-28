package ro.tweebyte.interactionservice.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserClient {

    @Value("${app.user.base-url}")
    private String baseUrl;

    private WebClient webClient = null;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<UserDto> getUserSummary(UUID userId) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("users/summary/{userId}").build(userId))
            .retrieve()
            .bodyToMono(UserDto.class)
            .onErrorMap(WebClientResponseException.class, e -> {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return new UserNotFoundException("User not found with id: " + userId);
                }
                return new InteractionException(e);
            });
    }


}
