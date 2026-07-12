/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;
import ro.tweebyte.userservice.util.MediaConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers StaleMediaCleanupService's sweep: stale ids not in the unioned reachable set
 * (local profile pictures + peer-service references + preview source edges + the default
 * avatar sentinel) are deleted in batches; the default avatar always survives; and an
 * unreachable reachability source surfaces (cleanupStaleMedia throws) while the scheduled
 * wrapper swallows it so the fixed-rate tick is never cancelled.
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
	void cleanupDeletesStaleUnreferencedIdsButKeepsReferencedAndDefaultAvatar() {
		UUID referenced = UUID.randomUUID();
		UUID stale = UUID.randomUUID();
		given(this.userRepository.findReferencedProfilePictureIds()).willReturn(List.of(referenced));
		given(this.tweetClient.getReferencedMediaIds()).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.interactionClient.getReferencedMediaIds()).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.mediaAssetRepository.findReferencedSourceMediaIds()).willReturn(List.of());
		given(this.mediaAssetRepository.findStaleIds(any()))
			.willReturn(List.of(referenced, stale, MediaConstants.DEFAULT_AVATAR_ID));

		this.cleanupService.cleanupStaleMedia();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.mediaAssetRepository).deleteAllByIdInBatch(captor.capture());
		assertThat(captor.getValue()).containsExactly(stale);
	}

	@Test
	void cleanupDeletesNothingWhenNoStaleRows() {
		given(this.userRepository.findReferencedProfilePictureIds()).willReturn(List.of());
		given(this.tweetClient.getReferencedMediaIds()).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.interactionClient.getReferencedMediaIds()).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.mediaAssetRepository.findReferencedSourceMediaIds()).willReturn(List.of());
		given(this.mediaAssetRepository.findStaleIds(any())).willReturn(List.of());

		this.cleanupService.cleanupStaleMedia();

		verify(this.mediaAssetRepository, never()).deleteAllByIdInBatch(anyList());
	}

	@Test
	void cleanupBatchesLargeOrphanSets() {
		// 250 stale orphans → batched into 100/100/50 chunks.
		List<UUID> orphans = new java.util.ArrayList<>();
		for (int i = 0; i < 250; i++) {
			orphans.add(UUID.randomUUID());
		}
		given(this.userRepository.findReferencedProfilePictureIds()).willReturn(List.of());
		given(this.tweetClient.getReferencedMediaIds()).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.interactionClient.getReferencedMediaIds()).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.mediaAssetRepository.findReferencedSourceMediaIds()).willReturn(List.of());
		given(this.mediaAssetRepository.findStaleIds(any())).willReturn(orphans);

		this.cleanupService.cleanupStaleMedia();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.mediaAssetRepository, org.mockito.Mockito.times(3)).deleteAllByIdInBatch(captor.capture());
		assertThat(captor.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
	}

	@Test
	void cleanupPropagatesPeerServiceFailureSoNothingIsDeleted() {
		given(this.userRepository.findReferencedProfilePictureIds()).willReturn(List.of());
		given(this.tweetClient.getReferencedMediaIds())
			.willReturn(CompletableFuture.failedFuture(new RuntimeException("tweet svc down")));
		lenient().when(this.interactionClient.getReferencedMediaIds())
			.thenReturn(CompletableFuture.completedFuture(List.of()));

		Throwable ex = catchThrowable(() -> this.cleanupService.cleanupStaleMedia());

		assertThat(ex).isNotNull();
		verify(this.mediaAssetRepository, never()).deleteAllByIdInBatch(anyList());
	}

	@Test
	void scheduledWrapperSwallowsFailureSoTheTickSurvives() {
		// The private fixed-rate wrapper must not let a failed sweep escape.
		given(this.userRepository.findReferencedProfilePictureIds())
			.willThrow(new RuntimeException("db unreachable"));

		// runCleanupStaleMedia wraps cleanupStaleMedia in a try/catch and returns normally.
		ReflectionTestUtils.invokeMethod(this.cleanupService, "runCleanupStaleMedia");

		verify(this.mediaAssetRepository, never()).deleteAllByIdInBatch(anyList());
	}

	@Test
	void lifecycleStartsAndShutsDownSchedulerCleanly() {
		ReflectionTestUtils.setField(this.cleanupService, "schedulerPoolSize", 1);
		ReflectionTestUtils.setField(this.cleanupService, "schedulerInitialDelayHours", 0L);
		ReflectionTestUtils.setField(this.cleanupService, "staleMediaPeriodHours", 1L);

		this.cleanupService.startCleanupTasks();
		// shutdownScheduler must terminate the pool without throwing.
		assertThatNoException().isThrownBy(() -> this.cleanupService.shutdownScheduler());
	}

	@Test
	void shutdownForcesShutdownNowWhenAwaitTimesOut() throws Exception {
		// A scheduler whose orderly awaitTermination reports a timeout (false) must escalate
		// to shutdownNow — the !awaitTermination arm the clean-termination test does not reach.
		ScheduledExecutorService scheduler = org.mockito.Mockito.mock(ScheduledExecutorService.class);
		given(scheduler.awaitTermination(1, TimeUnit.MINUTES)).willReturn(false);
		ReflectionTestUtils.setField(this.cleanupService, "scheduler", scheduler);

		this.cleanupService.shutdownScheduler();

		verify(scheduler).shutdown();
		verify(scheduler).shutdownNow();
	}

}
