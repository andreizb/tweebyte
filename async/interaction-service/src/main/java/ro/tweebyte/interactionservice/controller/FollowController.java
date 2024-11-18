package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.service.FollowService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @GetMapping("/{userId}/followers")
    public CompletableFuture<List<FollowDto>> getFollowers(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowers(userId);
    }

    @GetMapping("/{userId}/following")
    public CompletableFuture<List<FollowDto>> getFollowing(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowing(userId);
    }

    @GetMapping("/{userId}/followers/count")
    public CompletableFuture<Long> getFollowersCount(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowersCount(userId);
    }

    @GetMapping("/{userId}/followers/identifiers")
    public CompletableFuture<List<UUID>> getFollowersIdentifiers(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowedIdentifiers(userId);
    }

    @GetMapping("/{userId}/following/count")
    public CompletableFuture<Long> getFollowingCount(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowingCount(userId);
    }

    @GetMapping("/{userId}/requests")
    public CompletableFuture<List<FollowDto>> getFollowRequests(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return followService.getFollowRequests(userDetails.getUserId());
    }

    @PostMapping("/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<FollowDto> follow(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable(value = "followedId") UUID followedId) {
        return followService.follow(userDetails.getUserId(), followedId);
    }

    @PutMapping("/{followRequestId}/{status}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> updateFollowRequest(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @PathVariable(value = "followRequestId") UUID followRequestId,
                                                       @PathVariable(value = "status") FollowEntity.Status status) {
        return followService.updateFollowRequest(userDetails.getUserId(), followRequestId, status);
    }

    @DeleteMapping("/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> unfollow(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            @PathVariable(value = "followedId") UUID followedId) {
        return followService.unfollow(userDetails.getUserId(), followedId);
    }

}
