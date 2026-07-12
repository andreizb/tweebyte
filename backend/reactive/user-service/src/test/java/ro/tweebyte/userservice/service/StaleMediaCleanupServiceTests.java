/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers StaleMediaCleanupService's sweep: stale ids not in the unioned reachable set are
 * deleted in buffered batches, while any unreachable reachability source fails the tick
 * closed (onErrorResume) so nothing is deleted.
 */
@ExtendWith(MockitoExtension.class)
class StaleMediaCleanupServiceTests {

	@InjectMocks
	private StaleMediaCleanupService cleanupService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaAssetRepository mediaAssetRepository;

	@Mock
	private TweetClient tweetClient;

	@Mock
	private InteractionClient interactionClient;

	@Test
	void cleanupDeletesStaleUnreferencedIds() {
		UUID referenced = UUID.randomUUID();
		UUID stale = UUID.randomUUID();
		given(this.userRepository.findReferencedProfilePictureIds()).willReturn(Flux.just(referenced));
		given(this.tweetClient.getReferencedMediaIds()).willReturn(Flux.empty());
		given(this.interactionClient.getReferencedMediaIds()).willReturn(Flux.empty());
		given(this.mediaAssetRepository.findReferencedSourceMediaIds()).willReturn(Flux.empty());
		given(this.mediaAssetRepository.findStaleIds(any())).willReturn(Flux.just(referenced, stale));
		given(this.mediaAssetRepository.deleteAllById(any())).willReturn(Mono.empty());

		StepVerifier.create(this.cleanupService.cleanupStaleMedia()).verifyComplete();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.mediaAssetRepository).deleteAllById(captor.capture());
		assertThat(captor.getValue()).containsExactly(stale);
	}

	@Test
	void cleanupSourceFailureResumesEmptyAndDeletesNothing() {
		given(this.userRepository.findReferencedProfilePictureIds())
			.willReturn(Flux.error(new RuntimeException("db down")));
		lenient().when(this.tweetClient.getReferencedMediaIds()).thenReturn(Flux.empty());
		lenient().when(this.interactionClient.getReferencedMediaIds()).thenReturn(Flux.empty());
		lenient().when(this.mediaAssetRepository.findReferencedSourceMediaIds()).thenReturn(Flux.empty());

		StepVerifier.create(this.cleanupService.cleanupStaleMedia()).verifyComplete();

		verify(this.mediaAssetRepository, never()).deleteAllById(any());
	}

}
