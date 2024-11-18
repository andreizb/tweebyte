package ro.tweebyte.tweetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.service.HashtagService;
import ro.tweebyte.tweetservice.service.TweetService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/tweets")
@RequiredArgsConstructor
public class TweetController {

    private final TweetService tweetService;
    private final HashtagService hashtagService;

    @GetMapping("/feed")
    public CompletableFuture<List<TweetDto>> getFeed(@RequestHeader(value = "Authorization") String authorization,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        return tweetService.getUserFeed(userDetails.getUserId(), authorization);
    }

    @GetMapping("/{tweetId}")
    public CompletableFuture<TweetDto> getTweet(@RequestHeader(value = "Authorization") String authorization,
                                                @PathVariable(value = "tweetId") UUID tweetId) {
        return tweetService.getTweet(tweetId, authorization);
    }

    @GetMapping("/search/{searchTerm}")
    public CompletableFuture<List<TweetDto>> searchTweets(@PathVariable(value = "searchTerm") String searchTerm) {
        return tweetService.searchTweets(searchTerm);
    }

    @GetMapping("/search/hashtag/{searchTerm}")
    public CompletableFuture<List<TweetDto>> searchTweetsByHashtag(@PathVariable(value = "searchTerm") String searchTerm) {
        return tweetService.searchTweetsByHashtag(searchTerm);
    }

    @GetMapping("/hashtag/popular")
    public CompletableFuture<List<HashtagProjection>> computePopularHashtags() {
        return hashtagService.computePopularHashtags();
    }

    @GetMapping("/user/{userId}")
    public CompletableFuture<List<TweetDto>> getUserTweets(@RequestHeader(value = "Authorization") String authorization,
                                                           @PathVariable(value = "userId") UUID userId) {
        return tweetService.getUserTweets(userId, authorization);
    }

    @GetMapping("/{tweetId}/summary")
    public CompletableFuture<TweetDto> getTweetSummary(@PathVariable(value = "tweetId") UUID tweetId) {
        return tweetService.getTweetSummary(tweetId);
    }

    @GetMapping("/user/{userId}/summary")
    public CompletableFuture<List<TweetDto>> getUserTweetsSummary(@PathVariable(value = "userId") UUID userId) {
        return tweetService.getUserTweetsSummary(userId);
    }

    @PostMapping
    public CompletableFuture<TweetDto> createTweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                   @Valid @RequestBody TweetCreationRequest request) {
        return tweetService.createTweet(request.setUserId(userDetails.getUserId()));
    }

    @PutMapping("/{tweetId}")
    public CompletableFuture<Void> updateTweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable(value = "tweetId") UUID tweetId,
                                               @Valid @RequestBody TweetUpdateRequest request) {
        return tweetService.updateTweet(request.setId(tweetId).setUserId(userDetails.getUserId()));
    }

    @DeleteMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> deleteTweet(@PathVariable(value = "tweetId") UUID tweetId) {
        return tweetService.deleteTweet(tweetId);
    }

}
