package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.service.FollowService;

import java.util.List;
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
    public Mono<byte[]> getFollowing(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowing(userId, null);
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
    public Flux<FollowDto> getFollowRequests(@PathVariable(value = "userId") UUID userId) {
        return followService.getFollowRequests(userId);
    }

    @PostMapping("/{userId}/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<FollowDto> follow(@PathVariable(value = "userId") UUID userId,
                                               @PathVariable(value = "followedId") UUID followedId) {
        return followService.follow(userId, followedId);
    }

    @PutMapping("/{userId}/{followRequestId}/{status}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateFollowRequest(@PathVariable(value = "userId") UUID userId,
                                                       @PathVariable(value = "followRequestId") UUID followRequestId,
                                                       @PathVariable(value = "status") Status status) {
        return followService.updateFollowRequest(userId, followRequestId, status);
    }

    @DeleteMapping("/{userId}/{followedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unfollow(@PathVariable(value = "userId") UUID userId,
                                            @PathVariable(value = "followedId") UUID followedId) {
        return followService.unfollow(userId, followedId);
    }

}
