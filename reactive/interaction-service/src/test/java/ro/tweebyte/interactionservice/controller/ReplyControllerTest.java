package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.service.ReplyService;

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

	@Test
	void createReply_Success() {
		given(replyService.createReply(any(ReplyCreateRequest.class))).willReturn(Mono.just(replyDto));

		webTestClient
				.post()
				.uri("/replies/{userId}", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createRequest)
				.exchange()
				.expectStatus().isOk()
				.expectBody(ReplyDto.class)
				.isEqualTo(replyDto);
	}

	@Test
	void updateReply_Success() {
		given(replyService.updateReply(any(ReplyUpdateRequest.class))).willReturn(Mono.empty());

		webTestClient
				.put()
				.uri("/replies/{userId}/{replyId}", userId, replyId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateRequest)
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void deleteReply_Success() {
		given(replyService.deleteReply(eq(userId), eq(replyId))).willReturn(Mono.empty());

		webTestClient
				.delete()
				.uri("/replies/{userId}/{replyId}", userId, replyId)
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void getAllRepliesForTweet_Success() {
		given(replyService.getRepliesForTweet(eq(tweetId))).willReturn(Flux.just(replyDto));

		webTestClient
				.get()
				.uri("/replies/tweet/{tweetId}", tweetId)
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(ReplyDto.class)
				.hasSize(1)
				.contains(replyDto);
	}

	@Test
	void getReplyCountForTweet_Success() {
		given(replyService.getReplyCountForTweet(eq(tweetId))).willReturn(Mono.just(5L));

		webTestClient
				.get()
				.uri("/replies/tweet/{tweetId}/count", tweetId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Long.class)
				.isEqualTo(5L);
	}

	@Test
	void getTopReplyForTweet_Success() {
		given(replyService.getTopReplyForTweet(eq(tweetId))).willReturn(Mono.just(replyDto));

		webTestClient
				.get()
				.uri("/replies/tweet/{tweetId}/top", tweetId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(ReplyDto.class)
				.isEqualTo(replyDto);
	}
}
