package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.service.ReplyService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/replies")
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyService replyService;

    @PostMapping
    public CompletableFuture<ReplyDto> createReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                   @RequestBody ReplyCreateRequest request) {
        return replyService.createReply(request.setUserId(userDetails.getUserId()));
    }

    @PutMapping("/{replyId}")
    public CompletableFuture<Void> updateReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable UUID replyId,
                                               @RequestBody ReplyUpdateRequest request) {
        return replyService.updateReply(request.setId(replyId).setUserId(userDetails.getUserId()));
    }

    @DeleteMapping("/{replyId}")
    public CompletableFuture<Void> deleteReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable UUID replyId) {
        return replyService.deleteReply(userDetails.getUserId(), replyId);
    }

    @GetMapping("/tweet/{tweetId}")
    public CompletableFuture<List<ReplyDto>> getAllRepliesForTweet(@PathVariable UUID tweetId) {
        return replyService.getRepliesForTweet(tweetId);
    }

    @GetMapping("/tweet/{tweetId}/count")
    public CompletableFuture<Long> getReplyCountForTweet(@PathVariable UUID tweetId) {
        return replyService.getReplyCountForTweet(tweetId);
    }

    @GetMapping("/tweet/{tweetId}/top")
    public CompletableFuture<ReplyDto> getTopReplyForTweet(@PathVariable UUID tweetId) {
        return replyService.getTopReplyForTweet(tweetId);
    }

}
