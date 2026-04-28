package ro.tweebyte.interactionservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.exception.ClientException;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.util.ClientUtil;

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


    public UserDto getUserSummary(UUID userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "users/summary/" + userId)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return clientUtil.parseResponse(response, UserDto.class);
        } catch (ClientException e) {
            if (e.getResponse().statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new UserNotFoundException("User not found with id: " + userId);
            }

            throw new InteractionException(e);
        } catch (Exception e) {
            throw new InteractionException(e);
        }
    }

}
