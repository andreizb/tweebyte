/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.service.TweetInteractionsService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = TweetInteractionsController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class TweetInteractionsControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private TweetInteractionsService tweetInteractionsService;

	@Test
	void getTweetInteractions_Success_StreamsEntriesFromService() {
		UUID tweetOne = UUID.randomUUID();
		UUID tweetTwo = UUID.randomUUID();
		ReplyDto topReply = new ReplyDto();
		topReply.setId(UUID.randomUUID());
		given(this.tweetInteractionsService.getTweetInteractionsEntries(eq(List.of(tweetOne, tweetTwo))))
			.willReturn(Mono.just(List.of(new TweetInteractionsEntryDto(tweetOne, 10L, 2L, 1L, topReply),
					new TweetInteractionsEntryDto(tweetTwo, 0L, 0L, 0L, null))));

		this.webTestClient.post()
			.uri("/tweets/interactions")
			.bodyValue(List.of(tweetOne, tweetTwo))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[0].tweet_id")
			.isEqualTo(tweetOne.toString())
			.jsonPath("$[0].likes")
			.isEqualTo(10)
			.jsonPath("$[0].replies")
			.isEqualTo(2)
			.jsonPath("$[0].retweets")
			.isEqualTo(1)
			.jsonPath("$[1].tweet_id")
			.isEqualTo(tweetTwo.toString())
			.jsonPath("$[1].likes")
			.isEqualTo(0);
	}

}
