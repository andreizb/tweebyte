package ro.tweebyte.userservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.tweebyte.userservice.exception.FollowRetrievingException;
import ro.tweebyte.userservice.util.ClientUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Component
@RequiredArgsConstructor
public class InteractionClient {

    @Value("${app.interaction.base-url}")
    private String BASE_URL;

    private final ClientUtil clientUtil;

    private final ExecutorService executorService = ForkJoinPool.commonPool();

    private final HttpClient client = HttpClient.newBuilder().executor(executorService).build();

    public CompletableFuture<Long> getFollowersCount(UUID userId, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .uri(new URI(BASE_URL + "/follows/" + userId + "/followers/count")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> clientUtil.parseResponse(response, Long.class));
        } catch (Exception e) {
            throw new FollowRetrievingException();
        }
    }

    public CompletableFuture<Long> getFollowingCount(UUID userId, String authToken) {
        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Authorization", authToken)
                .uri(new URI(BASE_URL + "/follows/" + userId + "/following/count")).GET().build();

            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> clientUtil.parseResponse(response, Long.class));
        } catch (Exception e) {
            throw new FollowRetrievingException();
        }
    }

}
