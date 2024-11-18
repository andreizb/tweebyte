package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.service.RecommendationService;

import java.util.Collections;
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

	@BeforeEach
	public void setUp() {
		CustomUserDetails mockUserDetails = new CustomUserDetails(userId, "test-user");
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(mockUserDetails, null, Collections.emptyList())
		);
	}

	@Test
	@WithMockUser
	void findFollowRecommendations_Success() {
		given(recommendationService.recommendUsersToFollow(eq(userId))).willReturn(Flux.just(userDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/recommendations/follow")
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(UserDto.class)
			.hasSize(1)
			.contains(userDto);
	}

	@Test
	@WithMockUser
	void findHashtagRecommendations_Success() {
		given(recommendationService.fetchPopularHashtags()).willReturn(Flux.just(hashtagDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/recommendations/hashtags")
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(TweetDto.HashtagDto.class)
			.hasSize(1)
			.contains(hashtagDto);
	}
}
