package ro.tweebyte.interactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.service.RecommendationService;

@RestController
@RequestMapping(path = "/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping(path = "/follow")
    public Flux<UserDto> findFollowRecommendations(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return recommendationService.recommendUsersToFollow(userDetails.getUserId());
    }

    @GetMapping(path = "/hashtags")
    public Flux<TweetDto.HashtagDto> findHashtagRecommendations() {
        return recommendationService.fetchPopularHashtags();
    }

}
