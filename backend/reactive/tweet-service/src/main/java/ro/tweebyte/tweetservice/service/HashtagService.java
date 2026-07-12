/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetHashtagEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

@Service
@RequiredArgsConstructor
public class HashtagService {

	private final HashtagRepository hashtagRepository;

	private final TweetRepository tweetRepository;

	private final TweetHashtagRepository tweetHashtagRepository;

	private final HashtagMapper hashtagMapper;

	@Transactional
	public Mono<Void> handleTweetCreationHashtags(TweetRequest request) {
		return this.tweetRepository.findById(request.getId())
			.switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found with id " + request.getId())))
			.flatMapMany(tweetEntity -> {
				Set<String> hashtags = TweetTokenParser.extractHashtags(tweetEntity.getContent());
				return Flux.fromIterable(hashtags)
					.flatMap(this::findOrCreateHashtag)
					.flatMap(hashtag -> this.tweetHashtagRepository
						.save(new TweetHashtagEntity(tweetEntity.getId(), hashtag.getId())));
			})
			.then();
	}

	/**
	 * Rich tweet-update rebuild, batched to ~3 statements regardless of token count:
	 * <ol>
	 * <li>one {@code findByTextIn} SELECT resolves the texts that already exist in the
	 * catalogue (matching async {@code reconcileHashtags}, which resolves the to-add set
	 * with a single {@code findByTextIn});</li>
	 * <li>one multi-row INSERT creates the still-missing hashtag rows (no-op when none are
	 * missing);</li>
	 * <li>one {@code replaceLinks} statement that prunes the stale links and links every
	 * resolved hashtag (existing + freshly created) to the tweet — folded into a single
	 * data-modifying CTE when there are both stale and new links, so the link diff costs
	 * one round-trip rather than a separate delete then insert.</li>
	 * </ol>
	 * This replaces the former per-text {@code save} + per-link {@code save} flatMaps
	 * (one statement each). Per-text {@code findByText} would be an N+1 the async stack
	 * does not pay; links built from pre-seeded rows alone would silently drop novel tags.
	 * The stale-link prune is threaded in here (rather than issued as its own statement by
	 * the caller) purely so it can share the new-link insert's round-trip; the SELECT and
	 * the missing-hashtag INSERT both run first, so the link CTE never references a hashtag
	 * row that does not yet exist.
	 * @param tweetId the id of the tweet to link the hashtags to
	 * @param texts the hashtag texts to resolve, create if missing, and link
	 * @param staleLinkIds the hashtag ids whose existing links should be pruned in the same
	 * statement that adds the new links
	 * @return a completion signal that finishes once all links are reconciled
	 */
	public Mono<Void> linkTweetToHashtagsCreatingMissing(UUID tweetId, Set<String> texts,
			Collection<UUID> staleLinkIds) {
		return this.hashtagRepository.findByTextIn(texts)
			.collectMap(HashtagEntity::getText)
			.flatMap(existingByText -> {
				List<HashtagEntity> toCreate = texts.stream()
					.filter(text -> !existingByText.containsKey(text))
					.map(this.hashtagMapper::mapTextToEntity)
					.toList();
				// Insert the missing hashtags in one statement, then reconcile the links:
				// prune the stale links and add every resolved hashtag (existing + created)
				// in one statement (a CTE when both directions have work).
				return this.hashtagRepository.insertAll(toCreate)
					.thenMany(Flux.fromIterable(existingByText.values()))
					.concatWith(Flux.fromIterable(toCreate))
					.map(HashtagEntity::getId)
					.collectList()
					.flatMap(hashtagIds -> this.tweetHashtagRepository.replaceLinks(tweetId, staleLinkIds, hashtagIds));
			});
	}

	private Mono<HashtagEntity> findOrCreateHashtag(String text) {
		return this.hashtagRepository.findByText(text).switchIfEmpty(Mono.defer(() -> {
			HashtagEntity newHashtag = this.hashtagMapper.mapTextToEntity(text);
			return this.hashtagRepository.save(newHashtag);
		}));
	}

	public Flux<HashtagDto> computePopularHashtags() {
		return this.hashtagRepository.findPopularHashtags();
	}

}
