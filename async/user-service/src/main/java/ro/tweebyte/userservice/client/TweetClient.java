package ro.tweebyte.userservice.client;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.tweebyte.userservice.exception.UserException;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.util.ClientUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Component
@RequiredArgsConstructor
public class TweetClient {

    @Value("${app.tweet.base-url}")
    private String BASE_URL;

    private final ClientUtil clientUtil;

    private final ExecutorService executorService = ForkJoinPool.commonPool();

    private final HttpClient client = HttpClient.newBuilder().executor(executorService).build();

    @SneakyThrows
    @SuppressWarnings(value = "unchecked")
    public CompletableFuture<List<TweetDto>> getUserTweets(UUID userId, String authToken) {
        HttpRequest request = HttpRequest
            .newBuilder()
            .header("Authorization", authToken)
            .uri(new URI(BASE_URL + "/tweets/user/" + userId)).GET().build();

        try {
            return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> clientUtil.parseResponse(response, ArrayList.class));
        } catch (Exception e) {
            throw new UserException(e);
        }
    }

}
