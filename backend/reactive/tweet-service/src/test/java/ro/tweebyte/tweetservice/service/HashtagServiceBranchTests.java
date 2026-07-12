/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Covers {@code linkTweetToHashtagsCreatingMissing} — the rich tweet-update rebuild
 * untouched by {@link HashtagServiceTests} — exercising both the find-existing and the
 * create-missing branches.
 */
@ExtendWith(MockitoExtension.class)
class HashtagServiceBranchTests {

	@InjectMocks
	private HashtagService hashtagService;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private TweetHashtagRepository tweetHashtagRepository;

	@Mock
	private HashtagMapper hashtagMapper;

	private UUID tweetId;

	@BeforeEach
	void setUp() {
		this.tweetId = UUID.randomUUID();
	}

	@Test
	void linkTweetToHashtagsCreatingMissing_linksExistingAndCreatesMissing() {
		// "spring" pre-exists (link only); "java" is missing (created via the batched
		// insertAll), then BOTH ids are linked via the one-statement replaceLinks. With no
		// stale links the reconcile degrades to a plain insert. Exercises the create-missing
		// and link-all paths.
		UUID springId = UUID.randomUUID();
		UUID javaId = UUID.randomUUID();
		HashtagEntity existing = HashtagEntity.builder().id(springId).text("spring").build();
		given(this.hashtagRepository.findByTextIn(any())).willReturn(Flux.just(existing));

		HashtagEntity created = HashtagEntity.builder().id(javaId).text("java").build();
		given(this.hashtagMapper.mapTextToEntity("java")).willReturn(created);
		given(this.hashtagRepository.insertAll(List.of(created))).willReturn(Flux.just(created));
		given(this.tweetHashtagRepository.replaceLinks(eq(this.tweetId), eq(List.of()), any()))
			.willReturn(Mono.empty());

		StepVerifier
			.create(this.hashtagService.linkTweetToHashtagsCreatingMissing(this.tweetId, Set.of("spring", "java"),
					List.of()))
			.verifyComplete();

		verify(this.hashtagMapper).mapTextToEntity("java");
		// Exactly the missing hashtag goes through the batched insert; existing is not re-inserted.
		verify(this.hashtagRepository).insertAll(List.of(created));
		// Both resolved hashtag ids are linked in one batched statement, no stale prune.
		ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(this.tweetHashtagRepository).replaceLinks(eq(this.tweetId), eq(List.of()), captor.capture());
		assertThat(captor.getValue()).containsExactlyInAnyOrder(springId, javaId);
	}

	@Test
	void linkTweetToHashtagsCreatingMissing_allExisting_skipsCreate() {
		UUID springId = UUID.randomUUID();
		UUID javaId = UUID.randomUUID();
		HashtagEntity tagA = HashtagEntity.builder().id(springId).text("spring").build();
		HashtagEntity tagB = HashtagEntity.builder().id(javaId).text("java").build();
		given(this.hashtagRepository.findByTextIn(any())).willReturn(Flux.just(tagA, tagB));
		given(this.hashtagRepository.insertAll(List.of())).willReturn(Flux.empty());
		given(this.tweetHashtagRepository.replaceLinks(eq(this.tweetId), eq(List.of()), any()))
			.willReturn(Mono.empty());

		StepVerifier
			.create(this.hashtagService.linkTweetToHashtagsCreatingMissing(this.tweetId, Set.of("spring", "java"),
					List.of()))
			.verifyComplete();

		// All hashtags pre-exist → no row created (empty batched insert), both linked.
		verify(this.hashtagRepository).insertAll(List.of());
		ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(this.tweetHashtagRepository).replaceLinks(eq(this.tweetId), eq(List.of()), captor.capture());
		assertThat(captor.getValue()).containsExactlyInAnyOrder(springId, javaId);
	}

	@Test
	void linkTweetToHashtagsCreatingMissing_prunesStaleAndAddsNewInOneReconcile() {
		// A stale existing link is pruned in the SAME statement that adds the new link:
		// replaceLinks receives both the stale id and the resolved new id. "spring"
		// pre-exists (link only); the stale "old" link id is passed through to be removed.
		UUID springId = UUID.randomUUID();
		UUID staleId = UUID.randomUUID();
		HashtagEntity existing = HashtagEntity.builder().id(springId).text("spring").build();
		given(this.hashtagRepository.findByTextIn(any())).willReturn(Flux.just(existing));
		given(this.hashtagRepository.insertAll(List.of())).willReturn(Flux.empty());
		given(this.tweetHashtagRepository.replaceLinks(eq(this.tweetId), eq(List.of(staleId)), any()))
			.willReturn(Mono.empty());

		StepVerifier
			.create(this.hashtagService.linkTweetToHashtagsCreatingMissing(this.tweetId, Set.of("spring"),
					List.of(staleId)))
			.verifyComplete();

		// Stale link id and new link id are reconciled in one replaceLinks call.
		ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(this.tweetHashtagRepository).replaceLinks(eq(this.tweetId), eq(List.of(staleId)), captor.capture());
		assertThat(captor.getValue()).containsExactly(springId);
	}

}
