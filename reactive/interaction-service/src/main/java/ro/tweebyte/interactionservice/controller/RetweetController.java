package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.service.RetweetService;

import java.util.UUID;

@RestController
@RequestMapping(path = "/retweets")
@RequiredArgsConstructor
public class RetweetController {

    private final RetweetService retweetService;

    @PostMapping("/{userId}")
    public Mono<RetweetDto> createRetweet(@PathVariable(value = "userId") UUID userId,
                                          @RequestBody RetweetCreateRequest request) {
        return retweetService.createRetweet(request.setRetweeterId(userId));
    }

    @PutMapping("/{userId}/{retweetId}")
    public Mono<Void> updateRetweet(@PathVariable(value = "userId") UUID userId,
                                                 @PathVariable UUID retweetId,
                                                 @RequestBody RetweetUpdateRequest request) {
        return retweetService.updateRetweet(request.setId(retweetId).setRetweeterId(userId));
    }

    @DeleteMapping("/{userId}/{retweetId}")
    public Mono<Void> deleteRetweet(@PathVariable(value = "userId") UUID userId,
                                                 @PathVariable UUID retweetId) {
        return retweetService.deleteRetweet(retweetId, userId);
    }

    @GetMapping("/user/{userId}")
    public Flux<RetweetDto> getAllRetweetsByUser(@PathVariable UUID userId) {
        return retweetService.getRetweetsByUser(userId);
    }

    @GetMapping("/tweet/{tweetId}")
    public Flux<RetweetDto> getAllRetweetsOfTweet(@PathVariable UUID tweetId) {
        return retweetService.getRetweetsOfTweet(tweetId);
    }

    @GetMapping("/tweet/{tweetId}/count")
    public Mono<Long> getRetweetCountOfTweet(@PathVariable UUID tweetId) {
        return retweetService.getRetweetCountOfTweet(tweetId);
    }

}
