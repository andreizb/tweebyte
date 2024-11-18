package ro.tweebyte.interactionservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.exception.ClientException;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.util.ClientUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
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

    public TweetDto getTweetSummary(UUID tweetId) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "tweets/" + tweetId + "/summary")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return clientUtil.parseResponse(response, TweetDto.class);
        } catch (ClientException e) {
            if (e.getResponse().statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new TweetNotFoundException("Tweet not found with id: " + tweetId);
            }

            throw new InteractionException(e);
        } catch (Exception e) {
            throw new InteractionException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<TweetDto> getUserTweetsSummary(UUID userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "tweets/user/" + userId + "/summary")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return clientUtil.parseResponse(response, List.class);
        } catch (ClientException e) {
            if (e.getResponse().statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new TweetNotFoundException("Tweets not found for user id: " + userId);
            }

            throw new InteractionException(e);
        } catch (Exception e) {
            throw new InteractionException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<TweetDto.HashtagDto> getPopularHashtags() {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(BASE_URL + "tweets/hashtags/popular")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return clientUtil.parseResponse(response, List.class);
        } catch (Exception e) {
            throw new InteractionException(e);
        }
    }

}
