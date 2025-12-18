package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.service.FollowService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = FollowController.class)
class FollowControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private FollowService followService;

	private final UUID userId = UUID.randomUUID();
	private final UUID followedId = UUID.randomUUID();
	private final UUID followRequestId = UUID.randomUUID();
	private final FollowDto followDto = new FollowDto();

	@Test
	void getFollowers_Success() {
		given(followService.getFollowers(eq(userId), any())).willReturn(Flux.just(followDto));

		webTestClient.get()
				.uri("/follows/{userId}/followers", userId)
				.header("Authorization", "Bearer test-token")
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(FollowDto.class)
				.hasSize(1);
	}

	@Test
	void getFollowing_Success() throws Exception {
		byte[] response = new com.fasterxml.jackson.databind.ObjectMapper()
				.writeValueAsBytes(java.util.List.of(followDto));
		given(followService.getFollowing(eq(userId), any())).willReturn(Mono.just(response));

		webTestClient.get()
				.uri("/follows/{userId}/following", userId)
				.header("Authorization", "Bearer test-token")
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(FollowDto.class)
				.hasSize(1);
	}

	@Test
	void getFollowersCount_Success() {
		given(followService.getFollowersCount(eq(userId))).willReturn(Mono.just(10L));

		webTestClient.get()
				.uri("/follows/{userId}/followers/count", userId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Long.class)
				.isEqualTo(10L);
	}

	@Test
	void getFollowersIdentifiers_Success() {
		UUID followerId = UUID.randomUUID();
		given(followService.getFollowedIdentifiers(eq(userId))).willReturn(Flux.just(followerId));

		webTestClient.get()
				.uri("/follows/{userId}/followers/identifiers", userId)
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(UUID.class)
				.hasSize(1)
				.contains(followerId);
	}

	@Test
	void getFollowingCount_Success() {
		given(followService.getFollowingCount(eq(userId))).willReturn(Mono.just(5L));

		webTestClient.get()
				.uri("/follows/{userId}/following/count", userId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Long.class)
				.isEqualTo(5L);
	}

	@Test
	void getFollowRequests_Success() {
		given(followService.getFollowRequests(eq(userId))).willReturn(Flux.just(followDto));

		webTestClient.get()
				.uri("/follows/{userId}/requests", userId)
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(FollowDto.class)
				.hasSize(1);
	}

	@Test
	void follow_Success() {
		given(followService.follow(eq(userId), eq(followedId))).willReturn(Mono.just(followDto));

		webTestClient
				.post()
				.uri("/follows/{userId}/{followedId}", userId, followedId)
				.exchange()
				.expectStatus().isNoContent();
	}

	@Test
	void updateFollowRequest_Success() {
		given(followService.updateFollowRequest(eq(userId), eq(followRequestId), eq(Status.ACCEPTED)))
				.willReturn(Mono.empty());

		webTestClient
				.put()
				.uri("/follows/{userId}/{followRequestId}/{status}", userId, followRequestId, Status.ACCEPTED)
				.exchange()
				.expectStatus().isNoContent();
	}

	@Test
	void unfollow_Success() {
		given(followService.unfollow(eq(userId), eq(followedId))).willReturn(Mono.empty());

		webTestClient
				.delete()
				.uri("/follows/{userId}/{followedId}", userId, followedId)
				.exchange()
				.expectStatus().isNoContent();
	}
}
