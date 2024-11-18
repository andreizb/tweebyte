package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.service.RetweetService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/retweets")
@RequiredArgsConstructor
public class RetweetController {

    private final RetweetService retweetService;

    @PostMapping
    public CompletableFuture<RetweetDto> createRetweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @RequestBody RetweetCreateRequest request) {
        return retweetService.createRetweet(request.setRetweeterId(userDetails.getUserId()));
    }

    @PutMapping("/{retweetId}")
    public CompletableFuture<Void> updateRetweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @PathVariable UUID retweetId,
                                                 @RequestBody RetweetUpdateRequest request) {
        return retweetService.updateRetweet(request.setId(retweetId).setRetweeterId(userDetails.getUserId()));
    }

    @DeleteMapping("/{retweetId}")
    public CompletableFuture<Void> deleteRetweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @PathVariable UUID retweetId) {
        return retweetService.deleteRetweet(retweetId, userDetails.getUserId());
    }

    @GetMapping("/user/{userId}")
    public CompletableFuture<List<RetweetDto>> getAllRetweetsByUser(@PathVariable UUID userId) {
        return retweetService.getRetweetsByUser(userId);
    }

    @GetMapping("/tweet/{tweetId}")
    public CompletableFuture<List<RetweetDto>> getAllRetweetsOfTweet(@PathVariable UUID tweetId) {
        return retweetService.getRetweetsOfTweet(tweetId);
    }

    @GetMapping("/tweet/{tweetId}/count")
    public CompletableFuture<Long> getRetweetCountOfTweet(@PathVariable UUID tweetId) {
        return retweetService.getRetweetCountOfTweet(tweetId);
    }

}
