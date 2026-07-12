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
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TweetInteractionRow;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

/**
 * Consolidates a page's like/reply/retweet counts and top reply into one per-tweet snapshot, so
 * tweet-service enriches a whole page with a single inbound call. The four families keep their
 * dedicated per-tweet cache keys (shared with the single-id reads and the like/reply/retweet
 * deltas); only the cold-miss database load is collapsed — the four misses resolve from ONE
 * combined query ({@link ReplyRepository#findInteractionsForTweets}) shared across the families
 * via {@code cache()}, so interaction-service takes one r2dbc connection acquire per page instead
 * of four. Fewer acquires is the lever: reactor-pool's acquire handoff, not the sub-millisecond
 * queries, is what caps reactive throughput from around concurrency 100.
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

	public Mono<List<TweetInteractionsEntryDto>> getTweetInteractionsEntries(List<UUID> tweetIds) {
		// One combined database read for the whole page, shared across the cache miss-loaders with
		// cache(): the concurrent subscriptions trigger a single query (one acquire), not one per
		// family. A family that hits in full never subscribes it, so a fully-cached page issues no
		// query at all.
		Mono<Map<UUID, TweetInteractionRow>> rows = this.replyRepository
			.findInteractionsForTweets(tweetIds.toArray(UUID[]::new))
			.collectMap(TweetInteractionRow::tweetId)
			.cache();

		// The three count families read through ONE MGET (and one writeback) via getAllMulti — the
		// page's like/reply/retweet keys resolve together instead of three separate round-trips.
		// counts.get(0/1/2) are the like/reply/retweet maps, in the keyFns order below.
		Mono<List<Map<UUID, Long>>> countsMono = this.countCache.getAllMulti(tweetIds,
				List.of(LikeService::tweetLikeCountKey, ReplyService::replyCountKey, RetweetService::retweetCountKey),
				List.of(misses -> rows.map(byTweet -> countsForMisses(byTweet, misses, TweetInteractionRow::likeCount)),
						misses -> rows.map(byTweet -> countsForMisses(byTweet, misses, TweetInteractionRow::replyCount)),
						misses -> rows.map(byTweet -> countsForMisses(byTweet, misses, TweetInteractionRow::retweetCount))));
		Mono<Map<UUID, ReplyDto>> topRepliesMono = this.topReplyCache.getAll(tweetIds, ReplyService::topReplyKey,
				misses -> resolveTopReplies(rows, misses));

		return Mono.zip(countsMono, topRepliesMono).map(tuple -> {
			List<Map<UUID, Long>> counts = tuple.getT1();
			Map<UUID, Long> likeCounts = counts.get(0);
			Map<UUID, Long> replyCounts = counts.get(1);
			Map<UUID, Long> retweetCounts = counts.get(2);
			Map<UUID, ReplyDto> tops = tuple.getT2();

			// Assemble the per-tweet wire rows directly in request order — no intermediate
			// per-tweet interactions map. A tweet with no interactions defaults to zero
			// counts and an empty top reply, identical to the prior per-tweet toEntry contract.
			List<TweetInteractionsEntryDto> entries = new ArrayList<>(tweetIds.size());
			for (UUID tweetId : tweetIds) {
				entries.add(new TweetInteractionsEntryDto(tweetId, likeCounts.getOrDefault(tweetId, 0L),
						replyCounts.getOrDefault(tweetId, 0L), retweetCounts.getOrDefault(tweetId, 0L),
						tops.getOrDefault(tweetId, new ReplyDto())));
			}
			return entries;
		});
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
	private Mono<Map<UUID, ReplyDto>> resolveTopReplies(Mono<Map<UUID, TweetInteractionRow>> rowsMono,
			List<UUID> misses) {
		return rowsMono.flatMap(byTweet -> {
			List<TweetInteractionRow> withTopReply = new ArrayList<>();
			for (UUID id : misses) {
				TweetInteractionRow row = byTweet.get(id);
				if (row != null && row.topReplyId() != null) {
					withTopReply.add(row);
				}
			}
			if (withTopReply.isEmpty()) {
				return Mono.just(Map.of());
			}
			List<UUID> distinctAuthorIds = withTopReply.stream()
				.map(TweetInteractionRow::topReplyUserId)
				.distinct()
				.toList();
			return Flux.fromIterable(distinctAuthorIds)
				.flatMap(authorId -> this.userService.getUserSummary(authorId)
					.map(user -> Map.entry(authorId, user.getUserName())))
				.collectMap(Map.Entry::getKey, Map.Entry::getValue)
				.map(nameByAuthor -> {
					Map<UUID, ReplyDto> tops = new HashMap<>();
					for (TweetInteractionRow row : withTopReply) {
						tops.put(row.tweetId(),
								new ReplyDto(row.topReplyId(), row.topReplyUserId(), row.topReplyContent(),
										row.topReplyCreatedAt(), row.topReplyLikeCount())
									.setUserName(nameByAuthor.get(row.topReplyUserId())));
					}
					return tops;
				});
		});
	}

}
