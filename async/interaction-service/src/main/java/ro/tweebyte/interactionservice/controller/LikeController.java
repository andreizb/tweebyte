package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.service.LikeService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/likes")
public class LikeController {

    private final LikeService likeService;

    @GetMapping("/user/{userId}")
    public CompletableFuture<List<LikeDto>> getUserLikes(@PathVariable(value = "userId") UUID userId) {
        return likeService.getUserLikes(userId);
    }

    @GetMapping("/tweet/{tweetId}")
    public CompletableFuture<List<LikeDto>> getTweetLikes(@PathVariable(value = "tweetId") UUID tweetId) {
        return likeService.getTweetLikes(tweetId);
    }

    @GetMapping("/{tweetId}/count")
    public CompletableFuture<Long> getTweetLikesCount(@PathVariable(value = "tweetId") UUID tweetId) {
        return likeService.getTweetLikesCount(tweetId);
    }

    @PostMapping("/{userId}/tweets/{tweetId}")
    public CompletableFuture<LikeDto> likeTweet(@PathVariable(value = "userId") UUID userId,
            @PathVariable(value = "tweetId") UUID tweetId) {
        return likeService.likeTweet(userId, tweetId);
    }

    @DeleteMapping("/{userId}/tweets/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> unlikeTweet(@PathVariable(value = "userId") UUID userId,
            @PathVariable(value = "tweetId") UUID tweetId) {
        return likeService.unlikeTweet(userId, tweetId);
    }

    @PostMapping("/{userId}/replies/{replyId}")
    public CompletableFuture<LikeDto> likeReply(@PathVariable(value = "userId") UUID userId,
            @PathVariable(value = "replyId") UUID replyId) {
        return likeService.likeReply(userId, replyId);
    }

    @DeleteMapping("/{userId}/replies/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> unlikeReply(@PathVariable(value = "userId") UUID userId,
            @PathVariable(value = "replyId") UUID replyId) {
        return likeService.unlikeReply(userId, replyId);
    }

}
