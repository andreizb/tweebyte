/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TweetInteractionRow;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

/**
 * Consolidates a page's like/reply/retweet counts and top reply into one per-tweet snapshot, so
 * tweet-service enriches a whole page with a single inbound call. The four families keep their
 * dedicated per-tweet cache keys (shared with the single-id reads and the like/reply/retweet
 * deltas); only the cold-miss database load is collapsed — the four misses resolve from ONE
 * combined query ({@link ReplyRepository#findInteractionsForTweets}) run once and shared across the
 * families, so interaction-service takes one Hikari connection acquire per page instead of four.
 * Symmetric with the reactive stack's consolidated read.
 *
 * @author Andrei Zbarcea
 */
@Service
@RequiredArgsConstructor
public class TweetInteractionsService {

	private final ReplyRepository replyRepository;

	private final CountCache countCache;

	private final TopReplyCache topReplyCache;

	private final UserService userService;

	private final ExecutorService executorService;

	public CompletableFuture<List<TweetInteractionsEntryDto>> getTweetInteractionsEntries(List<UUID> tweetIds) {
		// The combined database read is LAZY: a miss-loader resolves it on demand and it runs at
		// most once across the families (memoised), matching the reactive stack's cache() gate. A
		// fully-cached page never invokes a loader, so it issues no query and takes no Hikari
		// acquire — the cold load alone costs one acquire, shared by every family that missed.
		Supplier<Map<UUID, TweetInteractionRow>> rows = memoize(
				() -> this.replyRepository.findInteractionsForTweets(tweetIds.toArray(new UUID[0]))
					.stream()
					.collect(Collectors.toMap(TweetInteractionRow::getTweetId, Function.identity())));

		// The three count families read through ONE MGET (and one writeback) via getAllMulti,
		// instead of three separate cache reads on three ioExecutor tasks. counts.get(0/1/2)
		// are the like/reply/retweet maps, in the keyFns order below.
		CompletableFuture<List<Map<UUID, Long>>> countsFuture = CompletableFuture.supplyAsync(
				() -> this.countCache.getAllMulti(tweetIds,
						List.of(LikeService::tweetLikeCountKey, ReplyService::replyCountKey,
								RetweetService::retweetCountKey),
						List.of(misses -> countsForMisses(rows.get(), misses, TweetInteractionRow::getLikeCount),
								misses -> countsForMisses(rows.get(), misses, TweetInteractionRow::getReplyCount),
								misses -> countsForMisses(rows.get(), misses, TweetInteractionRow::getRetweetCount))),
				this.executorService);
		CompletableFuture<Map<UUID, ReplyDto>> topRepliesFuture = CompletableFuture.supplyAsync(
				() -> this.topReplyCache.getAll(tweetIds, ReplyService::topReplyKey,
						misses -> resolveTopReplies(rows.get(), misses)),
				this.executorService);

		return CompletableFuture.allOf(countsFuture, topRepliesFuture).thenApply(v -> {
			List<Map<UUID, Long>> counts = countsFuture.join();
			return assembleEntries(tweetIds, counts.get(0), counts.get(1), counts.get(2), topRepliesFuture.join());
		});
	}

	// Once-only, thread-safe memoisation of the combined query: the two miss-loaders run on
	// separate pool tasks and may both reach for the rows, but computeIfAbsent on a single-key map
	// runs the supplier at most once — the analogue of the reactive stack's cache(). When neither
	// family misses, get() is never called and the supplier never runs.
	private static Supplier<Map<UUID, TweetInteractionRow>> memoize(Supplier<Map<UUID, TweetInteractionRow>> delegate) {
		Map<Boolean, Map<UUID, TweetInteractionRow>> holder = new ConcurrentHashMap<>(1);
		return () -> holder.computeIfAbsent(Boolean.TRUE, key -> delegate.get());
	}

	// Assemble the per-tweet wire rows directly in request order — no intermediate
	// per-tweet interactions map. A tweet with no interactions defaults to zero counts and
	// an empty top reply, identical to the prior per-tweet toEntry contract.
	private static List<TweetInteractionsEntryDto> assembleEntries(List<UUID> tweetIds, Map<UUID, Long> likes,
			Map<UUID, Long> replies, Map<UUID, Long> retweets, Map<UUID, ReplyDto> topReplies) {
		List<TweetInteractionsEntryDto> entries = new ArrayList<>(tweetIds.size());
		for (UUID tweetId : tweetIds) {
			entries.add(new TweetInteractionsEntryDto(tweetId, likes.getOrDefault(tweetId, 0L),
					replies.getOrDefault(tweetId, 0L), retweets.getOrDefault(tweetId, 0L),
					topReplies.getOrDefault(tweetId, new ReplyDto())));
		}
		return entries;
	}

	// A miss map for one count family: the family's column from the shared combined rows, with a
	// tweet the query reported no rows for defaulting to zero — the same contract as the per-family
	// GROUP BY loaders, whose omitted zeros the cache also materialises.
	private static Map<UUID, Long> countsForMisses(Map<UUID, TweetInteractionRow> byTweet, List<UUID> misses,
			Function<TweetInteractionRow, Long> column) {
		Map<UUID, Long> out = new HashMap<>();
		for (UUID id : misses) {
			TweetInteractionRow row = byTweet.get(id);
			Long value = (row != null) ? column.apply(row) : null;
			out.put(id, (value != null) ? value : 0L);
		}
		return out;
	}

	// Build top-reply DTOs for the missed tweets that have one, resolving the authors' usernames
	// through the cache-backed user summary (no interaction-DB acquire). The DISTINCT author ids
	// are resolved ONCE — many tweets on a page share the same top-reply author, so a per-tweet
	// read would issue that author's identical users:: GET once per tweet; deduping collapses it to
	// one read per author. A missed tweet with no top reply is omitted so TopReplyCache caches its
	// absent marker, matching loadTopRepliesForTweets.
	private Map<UUID, ReplyDto> resolveTopReplies(Map<UUID, TweetInteractionRow> byTweet, List<UUID> misses) {
		List<TweetInteractionRow> withTopReply = new ArrayList<>();
		for (UUID id : misses) {
			TweetInteractionRow row = byTweet.get(id);
			if (row != null && row.getTopReplyId() != null) {
				withTopReply.add(row);
			}
		}
		List<UUID> distinctAuthorIds = withTopReply.stream()
			.map(TweetInteractionRow::getTopReplyUserId)
			.distinct()
			.toList();
		Map<UUID, CompletableFuture<UserDto>> authorsById = distinctAuthorIds.stream()
			.collect(Collectors.toMap(Function.identity(), this.userService::getUserSummary));
		return CompletableFuture.allOf(authorsById.values().toArray(new CompletableFuture[0])).thenApply(v -> {
			Map<UUID, ReplyDto> tops = new HashMap<>();
			for (TweetInteractionRow row : withTopReply) {
				String userName = authorsById.get(row.getTopReplyUserId()).join().getUserName();
				tops.put(row.getTweetId(),
						new ReplyDto(row.getTopReplyId(), row.getTopReplyUserId(), row.getTopReplyContent(),
								row.getTopReplyCreatedAt(), row.getTopReplyLikeCount()).setUserName(userName));
			}
			return tops;
		}).join();
	}

}
