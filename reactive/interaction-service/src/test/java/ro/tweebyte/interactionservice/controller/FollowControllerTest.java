package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.CustomUserDetails;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.service.FollowService;

import java.util.Collections;
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

	@BeforeEach
	public void setUp() {
		CustomUserDetails mockUserDetails = new CustomUserDetails(userId, "test-user");
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(mockUserDetails, null, Collections.emptyList())
		);
	}

	@Test
	@WithMockUser
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
	@WithMockUser
	void getFollowing_Success() {
		given(followService.getFollowing(eq(userId), any())).willReturn(Flux.just(followDto));

		webTestClient.get()
			.uri("/follows/{userId}/following", userId)
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(FollowDto.class)
			.hasSize(1);
	}

	@Test
	@WithMockUser
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
	@WithMockUser
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
	@WithMockUser
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
	@WithMockUser
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
	@WithMockUser
	void follow_Success() {
		given(followService.follow(eq(userId), eq(followedId))).willReturn(Mono.just(followDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.post()
			.uri("/follows/{userId}/{followedId}", userId, followedId)
			.exchange()
			.expectStatus().isNoContent();
	}

	@Test
	@WithMockUser
	void updateFollowRequest_Success() {
		given(followService.updateFollowRequest(eq(userId), eq(followRequestId), eq(Status.ACCEPTED))).willReturn(Mono.empty());

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.put()
			.uri("/follows/{followRequestId}/{status}", followRequestId, Status.ACCEPTED)
			.exchange()
			.expectStatus().isNoContent();
	}

	@Test
	@WithMockUser
	void unfollow_Success() {
		given(followService.unfollow(eq(userId), eq(followedId))).willReturn(Mono.empty());

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.delete()
			.uri("/follows/{followedId}", followedId)
			.exchange()
			.expectStatus().isNoContent();
	}
}
