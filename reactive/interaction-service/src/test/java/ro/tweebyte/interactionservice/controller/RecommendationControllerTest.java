package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.service.RecommendationService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = RecommendationController.class)
class RecommendationControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private RecommendationService recommendationService;

	private final UUID userId = UUID.randomUUID();
	private final UserDto userDto = new UserDto();
	private final TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();

	@Test
	void findFollowRecommendations_Success() {
		given(recommendationService.recommendUsersToFollow(eq(userId))).willReturn(Flux.just(userDto));

		webTestClient
				.get()
				.uri("/recommendations/{userId}/follow", userId)
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(UserDto.class)
				.hasSize(1)
				.contains(userDto);
	}

	@Test
	void findHashtagRecommendations_Success() {
		given(recommendationService.fetchPopularHashtags()).willReturn(Flux.just(hashtagDto));

		webTestClient
				.get()
				.uri("/recommendations/hashtags")
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(TweetDto.HashtagDto.class)
				.hasSize(1)
				.contains(hashtagDto);
	}
}
