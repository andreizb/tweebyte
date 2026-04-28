package ro.tweebyte.interactionservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.service.LikeService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = LikeController.class)
class LikeControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private LikeService likeService;

	private final UUID userId = UUID.randomUUID();
	private final UUID tweetId = UUID.randomUUID();
	private final UUID replyId = UUID.randomUUID();
	private final LikeDto likeDto = new LikeDto();

	@Test
	void getUserLikes_Success() {
		given(likeService.getUserLikes(eq(userId))).willReturn(Flux.just(likeDto));

		webTestClient.get()
				.uri("/likes/user/{userId}", userId)
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(LikeDto.class)
				.hasSize(1);
	}

	@Test
	void getTweetLikes_Success() {
		given(likeService.getTweetLikes(eq(tweetId))).willReturn(Flux.just(likeDto));

		webTestClient.get()
				.uri("/likes/tweet/{tweetId}", tweetId)
				.exchange()
				.expectStatus().isOk()
				.expectBodyList(LikeDto.class)
				.hasSize(1);
	}

	@Test
	void getTweetLikesCount_Success() {
		given(likeService.getTweetLikesCount(eq(tweetId))).willReturn(Mono.just(10L));

		webTestClient.get()
				.uri("/likes/{tweetId}/count", tweetId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Long.class)
				.isEqualTo(10L);
	}

	@Test
	void likeTweet_Success() {
		given(likeService.likeTweet(eq(userId), eq(tweetId))).willReturn(Mono.just(likeDto));

		webTestClient
				.post()
				.uri("/likes/{userId}/tweets/{tweetId}", userId, tweetId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(LikeDto.class)
				.isEqualTo(likeDto);
	}

	@Test
	void unlikeTweet_Success() {
		given(likeService.unlikeTweet(eq(userId), eq(tweetId))).willReturn(Mono.empty());

		webTestClient
				.delete()
				.uri("/likes/{userId}/tweets/{tweetId}", userId, tweetId)
				.exchange()
				.expectStatus().isNoContent();
	}

	@Test
	void likeReply_Success() {
		given(likeService.likeReply(eq(userId), eq(replyId))).willReturn(Mono.just(likeDto));

		webTestClient
				.post()
				.uri("/likes/{userId}/replies/{replyId}", userId, replyId)
				.exchange()
				.expectStatus().isOk()
				.expectBody(LikeDto.class)
				.isEqualTo(likeDto);
	}

	@Test
	void unlikeReply_Success() {
		given(likeService.unlikeReply(eq(userId), eq(replyId))).willReturn(Mono.empty());

		webTestClient
				.delete()
				.uri("/likes/{userId}/replies/{replyId}", userId, replyId)
				.exchange()
				.expectStatus().isNoContent();
	}
}
