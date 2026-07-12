/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.tweebyte.interactionservice.service.MediaReferenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for MediaReferenceController — confirms it forwards the referenced-media query
 * straight to the service and returns its future.
 */
@ExtendWith(MockitoExtension.class)
class MediaReferenceControllerTests {

	@Mock
	private MediaReferenceService mediaReferenceService;

	@InjectMocks
	private MediaReferenceController mediaReferenceController;

	@Test
	void getReferencedMediaIds_DelegatesToService() throws Exception {
		UUID mediaId = UUID.randomUUID();
		given(this.mediaReferenceService.getReferencedMediaIds())
			.willReturn(CompletableFuture.completedFuture(List.of(mediaId)));

		List<UUID> result = this.mediaReferenceController.getReferencedMediaIds().get();

		assertThat(result).containsExactly(mediaId);
		verify(this.mediaReferenceService).getReferencedMediaIds();
	}

}
