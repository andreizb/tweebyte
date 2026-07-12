/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.service.MediaReferenceService;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for MediaReferenceController — confirms it forwards the referenced-media query
 * straight to the service and returns its Flux.
 */
@ExtendWith(MockitoExtension.class)
class MediaReferenceControllerTests {

	@Mock
	private MediaReferenceService mediaReferenceService;

	@InjectMocks
	private MediaReferenceController mediaReferenceController;

	@Test
	void getReferencedMediaIds_DelegatesToService() {
		UUID mediaId = UUID.randomUUID();
		given(this.mediaReferenceService.getReferencedMediaIds()).willReturn(Flux.just(mediaId));

		StepVerifier.create(this.mediaReferenceController.getReferencedMediaIds())
			.expectNext(mediaId)
			.verifyComplete();

		verify(this.mediaReferenceService).getReferencedMediaIds();
	}

}
