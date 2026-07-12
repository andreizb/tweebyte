/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.mockito.BDDMockito.given;

/**
 * Unit suite for MediaReferenceService — confirms the referenced-media ids stream
 * concatenates the reply-referenced ids ahead of the retweet-referenced ids.
 */
@ExtendWith(MockitoExtension.class)
class MediaReferenceServiceTests {

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private RetweetRepository retweetRepository;

	@InjectMocks
	private MediaReferenceService mediaReferenceService;

	@Test
	void getReferencedMediaIds_ConcatenatesReplyThenRetweetIds() {
		UUID replyMedia = UUID.randomUUID();
		UUID retweetMedia = UUID.randomUUID();
		given(this.replyRepository.findReferencedMediaIds()).willReturn(Flux.just(replyMedia));
		given(this.retweetRepository.findReferencedMediaIds()).willReturn(Flux.just(retweetMedia));

		StepVerifier.create(this.mediaReferenceService.getReferencedMediaIds())
			.expectNext(replyMedia)
			.expectNext(retweetMedia)
			.verifyComplete();
	}

}
