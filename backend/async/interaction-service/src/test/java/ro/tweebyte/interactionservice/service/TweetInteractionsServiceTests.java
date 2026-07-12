/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for {@link TweetInteractionsService} — the consolidated per-tweet snapshot
 * (like/reply/retweet counts + top reply) for the tweet-enrichment fan-out. Exercises:
 * <ul>
 * <li>the LAZY combined query: a fully-cached page issues NO database read (memoised loader
 * never runs);</li>
 * <li>the cold page that resolves every family's misses from ONE shared combined query,
 * defaulting an omitted tweet's count to zero;</li>
 * <li>the DISTINCT top-reply author resolution (shared authors resolved once) and the
 * no-top-reply omission.</li>
 * </ul>
 *
 * @author Andrei Zbarcea
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TweetInteractionsServiceTests {

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private CountCache countCache;

	@Mock
	private TopReplyCache topReplyCache;

	@Mock
	private UserService userService;

	@Mock
	private ExecutorService executorService;

	@InjectMocks
	private TweetInteractionsService tweetInteractionsService;

	@Captor
	private ArgumentCaptor<Function<List<UUID>, Map<UUID, ReplyDto>>> topReplyLoaderCaptor;

	@BeforeEach
	void setUp() {
		// Make supplyAsync(...) on this executor run synchronously so the combined snapshot
		// resolves inline on the test thread.
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
	}

	@Test
	void fullyCachedPage_issuesNoCombinedQuery() throws Exception {
		// Every count family and the top-reply cache hit in full, so no family invokes its
		// miss-loader: the lazy combined query never runs and no Hikari acquire is taken.
		UUID tweetOne = UUID.randomUUID();
		UUID tweetTwo = UUID.randomUUID();
		List<UUID> tweetIds = List.of(tweetOne, tweetTwo);

		given(this.countCache.getAllMulti(eq(tweetIds), anyList(), anyList()))
			.willReturn(List.of(Map.of(tweetOne, 5L, tweetTwo, 6L), Map.of(tweetOne, 1L, tweetTwo, 2L),
					Map.of(tweetOne, 3L, tweetTwo, 4L)));
		given(this.topReplyCache.getAll(eq(tweetIds), any(), any())).willReturn(Map.of());

		List<TweetInteractionsEntryDto> entries = this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds)
			.get();

		assertThat(entries).hasSize(2);
		// Entries are assembled in request order.
		assertThat(entries.get(0).tweetId()).isEqualTo(tweetOne);
		assertThat(entries.get(0).likes()).isEqualTo(5L);
		assertThat(entries.get(0).replies()).isEqualTo(1L);
		assertThat(entries.get(0).retweets()).isEqualTo(3L);
		assertThat(entries.get(1).tweetId()).isEqualTo(tweetTwo);
		assertThat(entries.get(1).likes()).isEqualTo(6L);
		// A tweet with no top reply defaults to an empty ReplyDto, not null.
		assertThat(entries.get(0).topReply()).isEqualTo(new ReplyDto());
		// The combined query is never issued on an all-hit page.
		verify(this.replyRepository, never()).findInteractionsForTweets(any());
	}

	@Test
	void coldPage_resolvesEveryFamilyFromOneSharedQuery() throws Exception {
		// All three count families miss the same two tweets; the shared combined query runs at
		// most ONCE across the families (memoised) and supplies each family's column. The third
		// tweet is absent from the query result and must default to zero.
		UUID withRow = UUID.randomUUID();
		UUID zeroRow = UUID.randomUUID();
		List<UUID> tweetIds = List.of(withRow, zeroRow);

		TweetInteractionRow row = mock(withRow, 7L, 8L, 9L, null, null);
		given(this.replyRepository.findInteractionsForTweets(any(UUID[].class))).willReturn(List.of(row));

		// getAllMulti drives each family's loader over the miss list and returns the loaded map.
		given(this.countCache.getAllMulti(eq(tweetIds), anyList(), anyList())).willAnswer(invocation -> {
			List<Function<List<UUID>, Map<UUID, Long>>> loaders = invocation.getArgument(2);
			// like / reply / retweet columns, in declaration order.
			return List.of(loaders.get(0).apply(tweetIds), loaders.get(1).apply(tweetIds),
					loaders.get(2).apply(tweetIds));
		});
		given(this.topReplyCache.getAll(eq(tweetIds), any(), any())).willReturn(Map.of());

		List<TweetInteractionsEntryDto> entries = this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds)
			.get();

		assertThat(entries).hasSize(2);
		assertThat(entries.get(0).likes()).isEqualTo(7L);
		assertThat(entries.get(0).replies()).isEqualTo(8L);
		assertThat(entries.get(0).retweets()).isEqualTo(9L);
		// The tweet the query reported no row for defaults to zero on every family.
		assertThat(entries.get(1).likes()).isZero();
		assertThat(entries.get(1).replies()).isZero();
		assertThat(entries.get(1).retweets()).isZero();
		// Memoisation: three families pulled the shared rows, but the query ran exactly once.
		verify(this.replyRepository, times(1)).findInteractionsForTweets(any(UUID[].class));
	}

	@Test
	void coldTopReplies_resolveDistinctAuthorsOnce() throws Exception {
		// Two tweets share the SAME top-reply author, plus a third tweet with no top reply.
		// The distinct author is resolved ONCE (not once per tweet), and the no-reply tweet is
		// omitted so its absent marker is cached.
		UUID tweetA = UUID.randomUUID();
		UUID tweetB = UUID.randomUUID();
		UUID tweetNoReply = UUID.randomUUID();
		UUID sharedAuthor = UUID.randomUUID();
		UUID replyA = UUID.randomUUID();
		UUID replyB = UUID.randomUUID();
		List<UUID> tweetIds = List.of(tweetA, tweetB, tweetNoReply);

		TweetInteractionRow rowA = mockTopReply(tweetA, replyA, sharedAuthor, "first", 11L);
		TweetInteractionRow rowB = mockTopReply(tweetB, replyB, sharedAuthor, "second", 22L);
		TweetInteractionRow rowNoReply = mock(tweetNoReply, 0L, 0L, 0L, null, null);
		given(this.replyRepository.findInteractionsForTweets(any(UUID[].class)))
			.willReturn(List.of(rowA, rowB, rowNoReply));

		UserDto author = new UserDto();
		author.setId(sharedAuthor);
		author.setUserName("sharedHandle");
		given(this.userService.getUserSummary(sharedAuthor)).willReturn(CompletableFuture.completedFuture(author));

		// Counts hit fully so only the top-reply loader runs the combined query.
		given(this.countCache.getAllMulti(eq(tweetIds), anyList(), anyList()))
			.willReturn(List.of(Map.of(), Map.of(), Map.of()));
		given(this.topReplyCache.getAll(eq(tweetIds), any(), this.topReplyLoaderCaptor.capture())).willAnswer(invocation -> {
			Function<List<UUID>, Map<UUID, ReplyDto>> loader = invocation.getArgument(2);
			return loader.apply(tweetIds);
		});

		List<TweetInteractionsEntryDto> entries = this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds)
			.get();

		Map<UUID, ReplyDto> resolved = this.topReplyLoaderCaptor.getValue().apply(tweetIds);
		assertThat(resolved).containsOnlyKeys(tweetA, tweetB);
		assertThat(resolved.get(tweetA).getId()).isEqualTo(replyA);
		assertThat(resolved.get(tweetA).getUserName()).isEqualTo("sharedHandle");
		assertThat(resolved.get(tweetA).getContent()).isEqualTo("first");
		assertThat(resolved.get(tweetA).getLikesCount()).isEqualTo(11L);
		assertThat(resolved.get(tweetB).getId()).isEqualTo(replyB);
		// The no-reply tweet is omitted so TopReplyCache can cache its absent marker.
		assertThat(resolved).doesNotContainKey(tweetNoReply);
		// Many tweets, one author: the shared author's summary is resolved once per call.
		verify(this.userService, times(2)).getUserSummary(sharedAuthor);
		assertThat(entries).hasSize(3);
	}

	// A minimal TweetInteractionRow projection stub: only the count columns + a null top reply.
	private static TweetInteractionRow mock(UUID tweetId, Long likes, Long replies, Long retweets, UUID topReplyId,
			UUID topReplyUserId) {
		TweetInteractionRow row = org.mockito.Mockito.mock(TweetInteractionRow.class);
		given(row.getTweetId()).willReturn(tweetId);
		given(row.getLikeCount()).willReturn(likes);
		given(row.getReplyCount()).willReturn(replies);
		given(row.getRetweetCount()).willReturn(retweets);
		given(row.getTopReplyId()).willReturn(topReplyId);
		given(row.getTopReplyUserId()).willReturn(topReplyUserId);
		return row;
	}

	// A TweetInteractionRow projection stub carrying a full top reply.
	private static TweetInteractionRow mockTopReply(UUID tweetId, UUID topReplyId, UUID authorId, String content,
			Long likeCount) {
		TweetInteractionRow row = org.mockito.Mockito.mock(TweetInteractionRow.class);
		given(row.getTweetId()).willReturn(tweetId);
		given(row.getLikeCount()).willReturn(0L);
		given(row.getReplyCount()).willReturn(0L);
		given(row.getRetweetCount()).willReturn(0L);
		given(row.getTopReplyId()).willReturn(topReplyId);
		given(row.getTopReplyUserId()).willReturn(authorId);
		given(row.getTopReplyContent()).willReturn(content);
		given(row.getTopReplyCreatedAt()).willReturn(LocalDateTime.now());
		given(row.getTopReplyLikeCount()).willReturn(likeCount);
		return row;
	}

}
