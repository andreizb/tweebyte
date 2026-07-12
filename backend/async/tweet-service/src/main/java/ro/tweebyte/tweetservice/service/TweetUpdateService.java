/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

/**
 * Rich tweet-update path used by {@code tweet-update} workload.
 *
 * <p>
 * Loads the tweet via {@code findByIdAndUserId} (which carries
 * {@code @EntityGraph(mentions, hashtags)}) and reconciles both collections against the
 * request tokens as a diff: links/rows whose text disappears are removed, tokens not yet
 * present are added, unchanged ones are left alone. The eager-loaded relations are
 * therefore load-bearing — they are the "before" side of the diff. Hibernate's
 * dirty-checking + cascade machinery emits only the delta DELETE/INSERT statements on
 * flush for {@code tweet_hashtag} and {@code mentions}.
 *
 * <p>
 * <b>Transaction boundary</b>: this method is invoked from
 * {@link TweetService#updateTweet(TweetUpdateRequest)} via
 * {@code CompletableFuture.supplyAsync(..., executorService)}. The supplyAsync lambda
 * runs on the executor thread, which sees {@code @Transactional} when invoked through the
 * Spring-proxied {@link TweetUpdateService} bean. The findById entity-graph load +
 * collection mutation + implicit flush all happen inside the same transaction.
 *
 * <p>
 * Mentions support is implemented for functional-equivalence parity with the reactive
 * stack, even though the {@code tweet-update} benchmark payload contains only
 * hashtags. Mention update requires a user-service lookup; that resolution happens in
 * {@link TweetService#updateTweet(TweetUpdateRequest)} <em>before</em> this transactional
 * bean is invoked, so the pooled DB connection is not held across it — the
 * {@code username -> userId} map is passed in already resolved.
 *
 * @author Andrei Zbarcea
 */
@Service
@RequiredArgsConstructor
public class TweetUpdateService {

	private final TweetRepository tweetRepository;

	private final HashtagRepository hashtagRepository;

	private final HashtagMapper hashtagMapper;

	private final MentionMapper mentionMapper;

	@Transactional
	public void updateTweetWithRelations(TweetUpdateRequest request, Map<String, UUID> resolvedMentions) {
		TweetEntity tweet = this.tweetRepository.findByIdAndUserId(request.getId(), request.getUserId())
			.orElseThrow(() -> new TweetNotFoundException("Tweet not found for id " + request.getId()));

		tweet.setContent(request.getContent());

		reconcileHashtags(tweet, request.getContent());
		reconcileMentions(tweet, request.getContent(), resolvedMentions);

		// Hibernate flushes at end-of-transaction: dirty content UPDATE +
		// collection diff drives DELETE/INSERT on tweet_hashtag + mentions.
	}

	// Owner-scoped delete: load via findByIdAndUserId so a non-owner (or missing
	// tweet) yields 404 instead of deleting another user's tweet. delete(entity)
	// (not deleteById) keeps the managed entity in the persistence context so
	// orphanRemoval on mentions and the tweet_hashtag cascade fire on flush.
	@Transactional
	public void deleteOwnedTweet(UUID userId, UUID tweetId) {
		TweetEntity tweet = this.tweetRepository.findByIdAndUserId(tweetId, userId)
			.orElseThrow(() -> new TweetNotFoundException("Tweet not found for id " + tweetId));
		this.tweetRepository.delete(tweet);
	}

	private void reconcileHashtags(TweetEntity tweet, String content) {
		Set<String> desired = TweetTokenParser.extractHashtags(content);

		// Drop links whose hashtag text is no longer present (removing from the
		// managed @ManyToMany set deletes the tweet_hashtag join row on flush).
		tweet.getHashtags().removeIf(h -> !desired.contains(h.getText()));

		Set<String> existingTexts = tweet.getHashtags()
			.stream()
			.map(HashtagEntity::getText)
			.collect(Collectors.toSet());
		Set<String> toAdd = desired.stream()
			.filter(text -> !existingTexts.contains(text))
			.collect(Collectors.toSet());

		if (toAdd.isEmpty()) {
			return;
		}

		// Bulk-load the to-add hashtag rows by text in one query; create any
		// that do not yet exist, then link them.
		Map<String, HashtagEntity> byText = new HashMap<>();
		this.hashtagRepository.findByTextIn(toAdd).forEach(h -> byText.put(h.getText(), h));

		toAdd.forEach(text -> {
			HashtagEntity hashtag = byText.computeIfAbsent(text,
					t -> this.hashtagRepository.save(this.hashtagMapper.mapTextToEntity(t)));
			tweet.getHashtags().add(hashtag);
		});
	}

	private void reconcileMentions(TweetEntity tweet, String content, Map<String, UUID> resolvedMentions) {
		Set<String> desired = TweetTokenParser.extractMentions(content);

		// orphanRemoval=true on tweet.mentions: removing a mention from the set
		// drives the DELETE on flush.
		tweet.getMentions().removeIf(m -> !desired.contains(m.getText()));

		Set<String> existingTexts = tweet.getMentions()
			.stream()
			.map(MentionEntity::getText)
			.collect(Collectors.toSet());

		for (String username : desired) {
			if (existingTexts.contains(username)) {
				continue;
			}
			// Username -> id resolution happened before the transaction (TweetService).
			// An unresolvable username (unknown user) is absent from the map and so is
			// silently skipped — matches reactive MentionService.handleTweetUpdateMentions.
			UUID userId = resolvedMentions.get(username);
			if (userId == null) {
				continue;
			}
			tweet.getMentions().add(this.mentionMapper.mapFieldsToEntity(userId, username, tweet));
		}
	}

}
