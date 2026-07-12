/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * Unit suite for MediaReferenceService — confirms the referenced-media ids union the
 * reply-referenced ids with the retweet-referenced ids (overlap left for the caller's Set).
 */
@ExtendWith(MockitoExtension.class)
class MediaReferenceServiceTests {

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private RetweetRepository retweetRepository;

	@Mock(lenient = true)
	private ExecutorService executorService;

	@InjectMocks
	private MediaReferenceService mediaReferenceService;

	@BeforeEach
	void runAsyncInline() {
		// getReferencedMediaIds() runs on supplyAsync(..., executorService) — run the task
		// inline so the CompletableFuture completes synchronously in this unit test.
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
	}

	@Test
	void getReferencedMediaIds_UnionsReplyAndRetweetIds() throws Exception {
		UUID replyMedia = UUID.randomUUID();
		UUID retweetMedia = UUID.randomUUID();
		given(this.replyRepository.findReferencedMediaIds()).willReturn(List.of(replyMedia));
		given(this.retweetRepository.findReferencedMediaIds()).willReturn(List.of(retweetMedia));

		List<UUID> result = this.mediaReferenceService.getReferencedMediaIds().get();

		assertThat(result).containsExactly(replyMedia, retweetMedia);
	}

}
