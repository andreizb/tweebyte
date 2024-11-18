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
import ro.tweebyte.interactionservice.service.ReplyService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ReplyController.class)
class ReplyControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private ReplyService replyService;

	private final UUID userId = UUID.randomUUID();
	private final UUID tweetId = UUID.randomUUID();
	private final UUID replyId = UUID.randomUUID();
	private final ReplyDto replyDto = new ReplyDto();
	private final ReplyCreateRequest createRequest = new ReplyCreateRequest();
	private final ReplyUpdateRequest updateRequest = new ReplyUpdateRequest();

	@BeforeEach
	public void setUp() {
		CustomUserDetails mockUserDetails = new CustomUserDetails(userId, "test-user");
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(mockUserDetails, null, Collections.emptyList())
		);
	}

	@Test
	@WithMockUser
	void createReply_Success() {
		given(replyService.createReply(any(ReplyCreateRequest.class))).willReturn(Mono.just(replyDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.post()
			.uri("/replies")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(createRequest)
			.exchange()
			.expectStatus().isOk()
			.expectBody(ReplyDto.class)
			.isEqualTo(replyDto);
	}

	@Test
	@WithMockUser
	void updateReply_Success() {
		given(replyService.updateReply(any(ReplyUpdateRequest.class))).willReturn(Mono.empty());

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.put()
			.uri("/replies/{replyId}", replyId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(updateRequest)
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	@WithMockUser
	void deleteReply_Success() {
		given(replyService.deleteReply(eq(userId), eq(replyId))).willReturn(Mono.empty());

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.delete()
			.uri("/replies/{replyId}", replyId)
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	@WithMockUser
	void getAllRepliesForTweet_Success() {
		given(replyService.getRepliesForTweet(eq(tweetId))).willReturn(Flux.just(replyDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/replies/tweet/{tweetId}", tweetId)
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(ReplyDto.class)
			.hasSize(1)
			.contains(replyDto);
	}

	@Test
	@WithMockUser
	void getReplyCountForTweet_Success() {
		given(replyService.getReplyCountForTweet(eq(tweetId))).willReturn(Mono.just(5L));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/replies/tweet/{tweetId}/count", tweetId)
			.exchange()
			.expectStatus().isOk()
			.expectBody(Long.class)
			.isEqualTo(5L);
	}

	@Test
	@WithMockUser
	void getTopReplyForTweet_Success() {
		given(replyService.getTopReplyForTweet(eq(tweetId))).willReturn(Mono.just(replyDto));

		webTestClient
			.mutateWith(SecurityMockServerConfigurers.csrf())
			.get()
			.uri("/replies/tweet/{tweetId}/top", tweetId)
			.exchange()
			.expectStatus().isOk()
			.expectBody(ReplyDto.class)
			.isEqualTo(replyDto);
	}
}
