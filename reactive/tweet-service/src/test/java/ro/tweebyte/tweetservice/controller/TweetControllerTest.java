package ro.tweebyte.tweetservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.service.HashtagService;
import ro.tweebyte.tweetservice.service.TweetService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = TweetController.class)
public class TweetControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TweetService tweetService;

    @MockBean
    private HashtagService hashtagService;

    private final TweetDto tweetDto = new TweetDto();
    private final HashtagDto hashtagDto = new HashtagDto();
    private final UUID tweetId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String searchTerm = "searchTerm";

    @BeforeEach
    public void setUp() {

        given(tweetService.getUserFeed(eq(userId), any())).willReturn(Flux.just(tweetDto));
        given(tweetService.getTweet(eq(tweetId), any())).willReturn(Mono.just(tweetDto));
        given(tweetService.searchTweets(eq(searchTerm))).willReturn(Flux.just(tweetDto));
        given(tweetService.searchTweetsByHashtag(eq(searchTerm))).willReturn(Flux.just(tweetDto));
        given(hashtagService.computePopularHashtags()).willReturn(Flux.just(hashtagDto));
        given(tweetService.getUserTweets(eq(userId), any())).willReturn(Flux.just(tweetDto));
        given(tweetService.getTweetSummary(eq(tweetId))).willReturn(Mono.just(tweetDto));
        given(tweetService.getUserTweetsSummary(eq(userId))).willReturn(Flux.just(tweetDto));
        given(tweetService.createTweet(any(TweetCreationRequest.class))).willReturn(Mono.just(tweetDto));
        given(tweetService.updateTweet(any(TweetUpdateRequest.class))).willReturn(Mono.empty());
        given(tweetService.deleteTweet(eq(tweetId))).willReturn(Mono.empty());
    }

    @Test
    public void searchTweets() {
        webTestClient.get().uri("/tweets/search/{searchTerm}", searchTerm)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TweetDto.class).hasSize(1);
    }

    @Test
    public void searchTweetsByHashtag() {
        webTestClient.get().uri("/tweets/search/hashtag/{searchTerm}", searchTerm)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TweetDto.class).hasSize(1);
    }

    @Test
    public void computePopularHashtags() {
        webTestClient.get().uri("/tweets/hashtag/popular")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(HashtagDto.class).hasSize(1);
    }

    @Test
    public void getTweetSummary() {
        webTestClient.get().uri("/tweets/{tweetId}/summary", tweetId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TweetDto.class).isEqualTo(tweetDto);
    }

    @Test
    public void getUserTweetsSummary() {
        webTestClient.get().uri("/tweets/user/{userId}/summary", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TweetDto.class).hasSize(1);
    }

    @Test
    public void createTweet() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setContent("asdfffffffffffffffffffffffffffffffffffffff");

        webTestClient
                .post().uri("/tweets/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TweetDto.class).isEqualTo(tweetDto);
    }

    @Test
    public void updateTweet() {
        TweetUpdateRequest request = new TweetUpdateRequest();
        webTestClient
                .put().uri("/tweets/{tweetId}", tweetId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    public void deleteTweet() {
        webTestClient
                .delete().uri("/tweets/{tweetId}", tweetId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    public void getUserTweets() {
        given(tweetService.getUserTweets(eq(userId), any())).willReturn(Flux.just(tweetDto));

        webTestClient
                .get()
                .uri("/tweets/user/{userId}", userId)
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TweetDto.class)
                .hasSize(1)
                .contains(tweetDto);
    }

    @Test
    public void getFeed() {
        given(tweetService.getUserFeed(eq(userId), any())).willReturn(Flux.just(tweetDto));

        webTestClient
                .get()
                .uri("/tweets/{userId}/feed", userId)
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TweetDto.class)
                .hasSize(1)
                .contains(tweetDto);
    }

    @Test
    public void getTweet() {
        given(tweetService.getTweet(eq(tweetId), any())).willReturn(Mono.just(tweetDto));

        webTestClient
                .get()
                .uri("/tweets/{tweetId}", tweetId)
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(TweetDto.class)
                .isEqualTo(tweetDto);
    }

}
