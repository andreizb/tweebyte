package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.service.FollowService;

import java.util.UUID;

@RestController
@RequestMapping(path = "/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @GetMapping("/{userId}/followers")
    public Flux<FollowDto> getFollowers(@PathVariable(value = "userId") UUID userId,
                                        @RequestHeader(name = "Authorization") String authorization) {
        return followService.getFollowers(userId, authorization);
    }

    @GetMapping("/{userId}/following")
    public Flux<FollowDto> getFollowing(@PathVariable(value = "userId") UUID userId,
                                        @RequestHeader(name = "Authorization") String authorization) {
        return followService.getFollowing(userId, authorization);
    }

    @GetMapping("/{userId}/followers/count")
    public Mono<Long> getFollowersCount(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowersCount(userId);
    }

    @GetMapping("/{userId}/followers/identifiers")
    public Flux<UUID> getFollowersIdentifiers(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowedIdentifiers(userId);
    }

    @GetMapping("/{userId}/following/count")
    public Mono<Long> getFollowingCount(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowingCount(userId);
    }

    @GetMapping("/{userId}/requests")
    public Flux<FollowDto> getFollowRequests(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return followService.getFollowRequests(userDetails.getUserId());
    }

//    @PostMapping("/{followedId}")
    @PostMapping("/{userId}/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
//    public Mono<FollowDto> follow(@AuthenticationPrincipal CustomUserDetails userDetails,
    public Mono<FollowDto> follow(@PathVariable(value = "userId") UUID userId,
                                               @PathVariable(value = "followedId") UUID followedId) {
//        return followService.follow(userDetails.getUserId(), followedId);
        return followService.follow(userId, followedId);
    }

    @PutMapping("/{followRequestId}/{status}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateFollowRequest(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @PathVariable(value = "followRequestId") UUID followRequestId,
                                                       @PathVariable(value = "status") Status status) {
        return followService.updateFollowRequest(userDetails.getUserId(), followRequestId, status);
    }

    @DeleteMapping("/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unfollow(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            @PathVariable(value = "followedId") UUID followedId) {
        return followService.unfollow(userDetails.getUserId(), followedId);
    }

}
