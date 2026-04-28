package ro.tweebyte.tweetservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.util.ClientUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Component
@RequiredArgsConstructor
public class UserClient {

    @Value("${app.user.base-url}")
    private String BASE_URL;

    private final ClientUtil clientUtil;

    private final ExecutorService executorService = ForkJoinPool.commonPool();

    private final HttpClient client = HttpClient.newBuilder().executor(executorService).build();

    public UserDto getUserSummary(String userName) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "users/summary/name/" + userName)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return clientUtil.parseResponse(response, UserDto.class);
        } catch (Exception e) {
            throw new UserNotFoundException("User not found for name: " + userName);
        }
    }

    public UserDto getUserSummary(UUID userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "users/summary/" + userId)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return clientUtil.parseResponse(response, UserDto.class);
        } catch (Exception e) {
            throw new UserNotFoundException("User not found for id: " + userId);
        }
    }

}
