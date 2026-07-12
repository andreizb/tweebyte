/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

@Service
@RequiredArgsConstructor
public class MentionService {

	private final TweetRepository tweetRepository;

	private final MentionMapper mentionMapper;

	private final UserService userService;

	@Transactional
	public void handleTweetCreationMentions(TweetRequest request) {
		TweetEntity tweetEntity = this.tweetRepository.findById(request.getId())
			.orElseThrow(() -> new TweetNotFoundException("Tweet not found with id " + request.getId()));

		Set<String> mentionTokens = TweetTokenParser.extractMentions(tweetEntity.getContent());

		List<CompletableFuture<MentionEntity>> futures = mentionTokens.stream()
			.map(userName -> createMention(userName, tweetEntity))
			.toList();

		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

		CompletableFuture<Set<MentionEntity>> mentionEntitiesFuture = allFutures.thenApply(v -> futures.stream()
			.map(CompletableFuture::join)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet()));

		mentionEntitiesFuture.thenAccept(mentions -> {
			tweetEntity.getMentions().clear();
			tweetEntity.getMentions().addAll(mentions);
		}).join();
	}

	private CompletableFuture<MentionEntity> createMention(String userName, TweetEntity tweetEntity) {
		return this.userService.getUserId(userName)
			.thenApply(userId -> this.mentionMapper.mapFieldsToEntity(userId, userName, tweetEntity))
			.handle((result, ex) -> {
				if (ex != null) {
					if (ex.getCause() instanceof UserNotFoundException) {
						return null;
					}
					else {
						throw new CompletionException(ex.getCause());
					}
				}

				return result;
			});
	}

}
