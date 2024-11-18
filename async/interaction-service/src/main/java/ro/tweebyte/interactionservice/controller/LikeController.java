package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
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

    @PostMapping("/tweets/{tweetId}")
    public CompletableFuture<LikeDto> likeTweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                @PathVariable(value = "tweetId") UUID tweetId) {
        return likeService.likeTweet(userDetails.getUserId(), tweetId);
    }

    @DeleteMapping("/tweets/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> unlikeTweet(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable(value = "tweetId") UUID tweetId) {
        return likeService.unlikeTweet(userDetails.getUserId(), tweetId);
    }

    @PostMapping("/replies/{replyId}")
    public CompletableFuture<LikeDto> likeReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                  @PathVariable(value = "replyId") UUID replyId) {
        return likeService.likeReply(userDetails.getUserId(), replyId);
    }

    @DeleteMapping("/replies/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> unlikeReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @PathVariable(value = "replyId") UUID replyId) {
        return likeService.unlikeReply(userDetails.getUserId(), replyId);
    }

}
