package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.service.RetweetService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = RetweetController.class)
class RetweetControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private RetweetService retweetService;

	private final UUID userId = UUID.randomUUID();
	private final UUID tweetId = UUID.randomUUID();
	private final UUID retweetId = UUID.randomUUID();
	private final RetweetDto retweetDto = new RetweetDto();
	private final RetweetCreateRequest createRequest = new RetweetCreateRequest();
	private final RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();

	@BeforeEach
	public void setUp() {
		CustomUserDetails mockUserDetails = new CustomUserDetails(userId, "test-user");
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(mockUserDetails, null, Collections.emptyList())
		);
	}

	@Test
	@WithMockUser
	void createRetweet_Success() {
		given(retweetService.createRetweet(any(RetweetCreateRequest.class))).willReturn(Mono.just(retweetDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.post()
			.uri("/retweets")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(createRequest)
			.exchange()
			.expectStatus().isOk()
			.expectBody(RetweetDto.class)
			.isEqualTo(retweetDto);
	}

	@Test
	@WithMockUser
	void updateRetweet_Success() {
		given(retweetService.updateRetweet(any(RetweetUpdateRequest.class))).willReturn(Mono.empty());

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.put()
			.uri("/retweets/{retweetId}", retweetId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(updateRequest)
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	@WithMockUser
	void deleteRetweet_Success() {
		given(retweetService.deleteRetweet(eq(retweetId), eq(userId))).willReturn(Mono.empty());

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.delete()
			.uri("/retweets/{retweetId}", retweetId)
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	@WithMockUser
	void getAllRetweetsByUser_Success() {
		given(retweetService.getRetweetsByUser(eq(userId))).willReturn(Flux.just(retweetDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/retweets/user/{userId}", userId)
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(RetweetDto.class)
			.hasSize(1)
			.contains(retweetDto);
	}

	@Test
	@WithMockUser
	void getAllRetweetsOfTweet_Success() {
		given(retweetService.getRetweetsOfTweet(eq(tweetId))).willReturn(Flux.just(retweetDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/retweets/tweet/{tweetId}", tweetId)
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(RetweetDto.class)
			.hasSize(1)
			.contains(retweetDto);
	}

	@Test
	@WithMockUser
	void getRetweetCountOfTweet_Success() {
		given(retweetService.getRetweetCountOfTweet(eq(tweetId))).willReturn(Mono.just(10L));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/retweets/tweet/{tweetId}/count", tweetId)
			.exchange()
			.expectStatus().isOk()
			.expectBody(Long.class)
			.isEqualTo(10L);
	}
}
