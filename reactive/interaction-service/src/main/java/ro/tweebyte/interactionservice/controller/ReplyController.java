package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.service.ReplyService;

import java.util.UUID;

@RestController
@RequestMapping(path = "/replies")
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyService replyService;

    @PostMapping
    public Mono<ReplyDto> createReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                      @RequestBody ReplyCreateRequest request) {
        return replyService.createReply(request.setUserId(userDetails.getUserId()));
    }

    @PutMapping("/{replyId}")
    public Mono<Void> updateReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable UUID replyId,
                                               @RequestBody ReplyUpdateRequest request) {
        return replyService.updateReply(request.setId(replyId).setUserId(userDetails.getUserId()));
    }

    @DeleteMapping("/{replyId}")
    public Mono<Void> deleteReply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable UUID replyId) {
        return replyService.deleteReply(userDetails.getUserId(), replyId);
    }

    @GetMapping("/tweet/{tweetId}")
    public Flux<ReplyDto> getAllRepliesForTweet(@PathVariable UUID tweetId) {
        return replyService.getRepliesForTweet(tweetId);
    }

    @GetMapping("/tweet/{tweetId}/count")
    public Mono<Long> getReplyCountForTweet(@PathVariable UUID tweetId) {
        return replyService.getReplyCountForTweet(tweetId);
    }

    @GetMapping("/tweet/{tweetId}/top")
    public Mono<ReplyDto> getTopReplyForTweet(@PathVariable UUID tweetId) {
        return replyService.getTopReplyForTweet(tweetId);
    }

}
