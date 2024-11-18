package ro.tweebyte.tweetservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.service.HashtagService;
import ro.tweebyte.tweetservice.service.TweetService;

import java.util.UUID;

@RestController
@RequestMapping(path = "/tweets")
@RequiredArgsConstructor
public class TweetController {

    private final TweetService tweetService;
    private final HashtagService hashtagService;

    @GetMapping("/feed")
    public Flux<TweetDto> getFeed(@RequestHeader(value = "Authorization") String authorization,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        return tweetService.getUserFeed(userDetails.getUserId(), authorization);
    }

    @GetMapping("/{tweetId}")
    public Mono<TweetDto> getTweet(@RequestHeader(value = "Authorization") String authorization,
                                   @PathVariable(value = "tweetId") UUID tweetId) {
        return tweetService.getTweet(tweetId, authorization);
    }

    @GetMapping("/search/{searchTerm}")
    public Flux<TweetDto> searchTweets(@PathVariable(value = "searchTerm") String searchTerm) {
        return tweetService.searchTweets(searchTerm);
    }

    @GetMapping("/search/hashtag/{searchTerm}")
    public Flux<TweetDto> searchTweetsByHashtag(@PathVariable(value = "searchTerm") String searchTerm) {
        return tweetService.searchTweetsByHashtag(searchTerm);
    }

    @GetMapping("/hashtag/popular")
    public Flux<HashtagDto> computePopularHashtags() {
        return hashtagService.computePopularHashtags();
    }

    @GetMapping("/user/{userId}")
    public Flux<TweetDto> getUserTweets(@RequestHeader(value = "Authorization") String authorization,
                                        @PathVariable(value = "userId") UUID userId) {
        return tweetService.getUserTweets(userId, authorization);
    }

    @GetMapping("/{tweetId}/summary")
    public Mono<TweetDto> getTweetSummary(@PathVariable(value = "tweetId") UUID tweetId) {
        return tweetService.getTweetSummary(tweetId);
    }

    @GetMapping("/user/{userId}/summary")
    public Flux<TweetDto> getUserTweetsSummary(@PathVariable(value = "userId") UUID userId) {
        return tweetService.getUserTweetsSummary(userId);
    }

    @PostMapping
    public Mono<TweetDto> createTweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                      @Valid @RequestBody TweetCreationRequest request) {
        return tweetService.createTweet(request.setUserId(userDetails.getUserId()));
    }

    @PutMapping("/{tweetId}")
    public Mono<Void> updateTweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @PathVariable(value = "tweetId") UUID tweetId,
                                  @Valid @RequestBody TweetUpdateRequest request) {
        return tweetService.updateTweet(request.setId(tweetId).setUserId(userDetails.getUserId()));
    }

    @DeleteMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTweet(@PathVariable(value = "tweetId") UUID tweetId) {
        return tweetService.deleteTweet(tweetId);
    }
}
