/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TweetInteractionRow;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for {@link TweetInteractionsService} — the consolidated per-tweet snapshot read.
 * Exercises the cold-miss combined-query path (counts assembled from the shared rows with the
 * GROUP-BY-omitted zeros materialised), the top-reply author dedup + username resolution, the
 * request-order assembly, the no-top-reply default, and the fully-cached page that fires no
 * database query.
 */
@ExtendWith(MockitoExtension.class)
class TweetInteractionsServiceTests {

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private CountCache countCache;

	@Mock
	private TopReplyCache topReplyCache;

	@Mock
	private UserService userService;

	@InjectMocks
	private TweetInteractionsService tweetInteractionsService;

	@Captor
	private ArgumentCaptor<Function<List<UUID>, Mono<Map<UUID, ReplyDto>>>> topReplyLoaderCaptor;

	@Captor
	private ArgumentCaptor<List<Function<List<UUID>, Mono<Map<UUID, Long>>>>> countLoadersCaptor;

	private UUID tweetOne;

	private UUID tweetTwo;

	@BeforeEach
	void setUp() {
		this.tweetOne = UUID.randomUUID();
		this.tweetTwo = UUID.randomUUID();
		// The combined-query Mono is assembled eagerly (rows = findInteractionsForTweets(...)
		// .collectMap(...).cache()) before the cache outcome is known, so the repository must
		// return a non-null publisher at assembly time even on a fully-cached page that never
		// subscribes it. Tests exercising the cold path override this with real rows.
		lenient().when(this.replyRepository.findInteractionsForTweets(any())).thenReturn(Flux.empty());
	}

	@Test
	void getTweetInteractionsEntries_AllCached_AssemblesInRequestOrderWithoutQuery() {
		// Counts hit for both tweets across all three families; both top replies hit. The combined
		// query is assembled but must never be SUBSCRIBED — model that by making the row source
		// error on subscription (Flux.defer defers the error to subscribe time, so the eager
		// assembly call is harmless). The wire rows come back in the request id order.
		given(this.replyRepository.findInteractionsForTweets(any()))
			.willReturn(Flux.defer(() -> Flux.error(new AssertionError("combined query must not be subscribed on a fully-cached page"))));
		ReplyDto topOne = new ReplyDto(UUID.randomUUID(), UUID.randomUUID(), "first", LocalDateTime.now(), 4L);

		given(this.countCache.getAllMulti(any(), anyList(), anyList())).willReturn(Mono.just(List.of(
				Map.of(this.tweetOne, 10L, this.tweetTwo, 20L), Map.of(this.tweetOne, 1L, this.tweetTwo, 2L),
				Map.of(this.tweetOne, 5L, this.tweetTwo, 6L))));
		given(this.topReplyCache.getAll(any(), any(), any())).willReturn(Mono.just(Map.of(this.tweetOne, topOne)));

		StepVerifier.create(this.tweetInteractionsService.getTweetInteractionsEntries(List.of(this.tweetOne, this.tweetTwo)))
			.assertNext(entries -> {
				assertThat(entries).hasSize(2);
				TweetInteractionsEntryDto first = entries.get(0);
				assertThat(first.tweetId()).isEqualTo(this.tweetOne);
				assertThat(first.likes()).isEqualTo(10L);
				assertThat(first.replies()).isEqualTo(1L);
				assertThat(first.retweets()).isEqualTo(5L);
				assertThat(first.topReply()).isSameAs(topOne);
				TweetInteractionsEntryDto second = entries.get(1);
				assertThat(second.tweetId()).isEqualTo(this.tweetTwo);
				assertThat(second.likes()).isEqualTo(20L);
				// A tweet with no cached top reply defaults to an empty ReplyDto (not null).
				assertThat(second.topReply()).isNotNull();
				assertThat(second.topReply().getId()).isNull();
			})
			.verifyComplete();
	}

	@Test
	void getTweetInteractionsEntries_MissingCounts_DefaultToZero() {
		// The count maps omit tweetTwo entirely (e.g. the loader's GROUP BY had no row); the
		// assembly must default the absent tweet's counts to zero rather than NPE.
		given(this.countCache.getAllMulti(any(), anyList(), anyList()))
			.willReturn(Mono.just(List.of(Map.of(this.tweetOne, 7L), Map.of(this.tweetOne, 3L), Map.of(this.tweetOne, 2L))));
		given(this.topReplyCache.getAll(any(), any(), any())).willReturn(Mono.just(Map.of()));

		StepVerifier.create(this.tweetInteractionsService.getTweetInteractionsEntries(List.of(this.tweetOne, this.tweetTwo)))
			.assertNext(entries -> {
				assertThat(entries).hasSize(2);
				assertThat(entries.get(1).tweetId()).isEqualTo(this.tweetTwo);
				assertThat(entries.get(1).likes()).isZero();
				assertThat(entries.get(1).replies()).isZero();
				assertThat(entries.get(1).retweets()).isZero();
			})
			.verifyComplete();
	}

	@Test
	void getTweetInteractionsEntries_ColdTopReplyMiss_ResolvesAuthorOnceAndBuildsDto() {
		// Both tweets share ONE top-reply author: the loader must resolve that author's username a
		// single time (dedup) and stamp it onto each tweet's ReplyDto. Drive the real loader the
		// service passes to TopReplyCache.getAll so resolveTopReplies + the combined query run.
		UUID sharedAuthor = UUID.randomUUID();
		UUID replyOneId = UUID.randomUUID();
		UUID replyTwoId = UUID.randomUUID();
		LocalDateTime when = LocalDateTime.of(2026, 6, 20, 12, 0);

		TweetInteractionRow rowOne = new TweetInteractionRow(this.tweetOne, 9L, 1L, 0L, replyOneId, sharedAuthor,
				"reply one", when, 4L);
		TweetInteractionRow rowTwo = new TweetInteractionRow(this.tweetTwo, 8L, 2L, 0L, replyTwoId, sharedAuthor,
				"reply two", when, 5L);

		given(this.replyRepository.findInteractionsForTweets(any())).willReturn(Flux.just(rowOne, rowTwo));
		given(this.countCache.getAllMulti(any(), anyList(), anyList())).willReturn(Mono.just(List.of(
				Map.of(this.tweetOne, 9L, this.tweetTwo, 8L), Map.of(this.tweetOne, 1L, this.tweetTwo, 2L),
				Map.of(this.tweetOne, 0L, this.tweetTwo, 0L))));
		// The cache delegates the misses to the real loader; capture and invoke it with both ids.
		given(this.topReplyCache.getAll(any(), any(), this.topReplyLoaderCaptor.capture()))
			.willAnswer(invocation -> this.topReplyLoaderCaptor.getValue().apply(List.of(this.tweetOne, this.tweetTwo)));

		UserDto author = new UserDto();
		author.setId(sharedAuthor);
		author.setUserName("shared-author");
		given(this.userService.getUserSummary(sharedAuthor)).willReturn(Mono.just(author));

		StepVerifier.create(this.tweetInteractionsService.getTweetInteractionsEntries(List.of(this.tweetOne, this.tweetTwo)))
			.assertNext(entries -> {
				assertThat(entries).hasSize(2);
				ReplyDto firstTop = entries.get(0).topReply();
				assertThat(firstTop.getId()).isEqualTo(replyOneId);
				assertThat(firstTop.getContent()).isEqualTo("reply one");
				assertThat(firstTop.getUserName()).isEqualTo("shared-author");
				ReplyDto secondTop = entries.get(1).topReply();
				assertThat(secondTop.getId()).isEqualTo(replyTwoId);
				assertThat(secondTop.getUserName()).isEqualTo("shared-author");
			})
			.verifyComplete();

		// Dedup: the shared author is resolved exactly once even though two tweets reference it.
		verify(this.userService, times(1)).getUserSummary(sharedAuthor);
	}

	@Test
	void getTweetInteractionsEntries_ColdCountMiss_LoadersResolveColumnsFromSharedRowsWithZeroDefault() {
		// Drive the THREE count-family loaders the service hands to getAllMulti, so countsForMisses
		// runs: like/reply/retweet columns come from the shared combined query, and a tweet the
		// query reported no row for defaults to zero per family.
		LocalDateTime when = LocalDateTime.of(2026, 6, 20, 8, 30);
		TweetInteractionRow rowOne = new TweetInteractionRow(this.tweetOne, 11L, 5L, 3L, null, null, null, when, null);
		// tweetTwo has no row → every family must default it to zero.
		given(this.replyRepository.findInteractionsForTweets(any())).willReturn(Flux.just(rowOne));
		given(this.topReplyCache.getAll(any(), any(), any())).willReturn(Mono.just(Map.of()));
		// Capture the loaders, return the (irrelevant here) hit maps, then invoke each loader below.
		given(this.countCache.getAllMulti(any(), anyList(), this.countLoadersCaptor.capture()))
			.willReturn(Mono.just(List.of(Map.of(), Map.of(), Map.of())));

		StepVerifier.create(this.tweetInteractionsService.getTweetInteractionsEntries(List.of(this.tweetOne, this.tweetTwo)))
			.assertNext(entries -> assertThat(entries).hasSize(2))
			.verifyComplete();

		List<UUID> misses = List.of(this.tweetOne, this.tweetTwo);
		List<Function<List<UUID>, Mono<Map<UUID, Long>>>> loaders = this.countLoadersCaptor.getValue();
		// loaders.get(0/1/2) = like/reply/retweet (the keyFns order in the service).
		StepVerifier.create(loaders.get(0).apply(misses))
			.assertNext(likes -> assertThat(likes).containsEntry(this.tweetOne, 11L).containsEntry(this.tweetTwo, 0L))
			.verifyComplete();
		StepVerifier.create(loaders.get(1).apply(misses))
			.assertNext(replies -> assertThat(replies).containsEntry(this.tweetOne, 5L).containsEntry(this.tweetTwo, 0L))
			.verifyComplete();
		StepVerifier.create(loaders.get(2).apply(misses))
			.assertNext(rts -> assertThat(rts).containsEntry(this.tweetOne, 3L).containsEntry(this.tweetTwo, 0L))
			.verifyComplete();
	}

	@Test
	void getTweetInteractionsEntries_ColdMissNoTopReplies_LoaderReturnsEmptyMap() {
		// Every missed tweet either has no row or a null topReplyId, so resolveTopReplies must
		// short-circuit to an empty map without touching user-service.
		TweetInteractionRow noReply = new TweetInteractionRow(this.tweetOne, 3L, 0L, 0L, null, null, null, null, null);
		given(this.replyRepository.findInteractionsForTweets(any())).willReturn(Flux.just(noReply));
		given(this.countCache.getAllMulti(any(), anyList(), anyList())).willReturn(Mono
			.just(List.of(Map.of(this.tweetOne, 3L), Map.of(this.tweetOne, 0L), Map.of(this.tweetOne, 0L))));
		given(this.topReplyCache.getAll(any(), any(), this.topReplyLoaderCaptor.capture()))
			.willAnswer(invocation -> this.topReplyLoaderCaptor.getValue().apply(List.of(this.tweetOne)));

		StepVerifier.create(this.tweetInteractionsService.getTweetInteractionsEntries(List.of(this.tweetOne)))
			.assertNext(entries -> {
				assertThat(entries).hasSize(1);
				assertThat(entries.get(0).topReply().getId()).isNull();
			})
			.verifyComplete();

		verify(this.userService, never()).getUserSummary(any());
	}

}
