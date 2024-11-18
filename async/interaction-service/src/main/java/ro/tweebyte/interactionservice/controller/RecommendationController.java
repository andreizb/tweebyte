package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.service.RecommendationService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping(path = "/follow")
    public CompletableFuture<List<UserDto>> findFollowRecommendations(@AuthenticationPrincipal CustomUserDetails userDetails, @PageableDefault Pageable pageable) {
        return recommendationService.recommendUsersToFollow(userDetails.getUserId(), pageable);
    }

    @GetMapping(path = "/hashtags")
    public CompletableFuture<List<TweetDto.HashtagDto>> findHashtagRecommendations() {
        return recommendationService.computePopularHashtags();
    }

    @PostMapping(path = "/tweet/summary")
    public CompletableFuture<List<TweetSummaryDto>> findTweetSummaries(@RequestBody List<UUID> tweetIds) {
        return recommendationService.findTweetSummaries(tweetIds);
    }

}
