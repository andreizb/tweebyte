/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
class TweetServiceTests {

	@Mock
	private TweetClient tweetClient;

	@InjectMocks
	private TweetService tweetService;

	@Test
	void testGetTweetSummary() throws Exception {
		UUID tweetId = UUID.randomUUID();
		TweetDto expected = new TweetDto();

		given(this.tweetClient.getTweetSummary(tweetId)).willReturn(CompletableFuture.completedFuture(expected));

		TweetDto result = this.tweetService.getTweetSummary(tweetId).get();

		assertThat(result).isEqualTo(expected);
		verify(this.tweetClient).getTweetSummary(tweetId);
	}

	@Test
	void testGetUserTweetsSummary() throws Exception {
		UUID userId = UUID.randomUUID();
		List<TweetSummaryDto> expected = Collections.singletonList(new TweetSummaryDto());

		given(this.tweetClient.getUserTweetsSummary(userId)).willReturn(CompletableFuture.completedFuture(expected));

		List<TweetSummaryDto> result = this.tweetService.getUserTweetsSummary(userId).get();

		assertThat(result).isEqualTo(expected);
		verify(this.tweetClient).getUserTweetsSummary(userId);
	}

	@Test
	void testGetPopularHashtags() throws Exception {
		List<TweetDto.HashtagDto> expected = Collections.singletonList(new TweetDto.HashtagDto());
		given(this.tweetClient.getPopularHashtags()).willReturn(CompletableFuture.completedFuture(expected));

		List<TweetDto.HashtagDto> result = this.tweetService.getPopularHashtags().get();

		assertThat(result).isEqualTo(expected);
		verify(this.tweetClient).getPopularHashtags();
	}

}
