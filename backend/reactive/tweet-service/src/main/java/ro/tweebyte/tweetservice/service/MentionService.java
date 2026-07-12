/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

@Service
@RequiredArgsConstructor
public class MentionService {

	private final MentionRepository mentionRepository;

	private final TweetRepository tweetRepository;

	private final MentionMapper mentionMapper;

	private final UserService userService;

	@Transactional
	public Mono<Void> handleTweetCreationMentions(TweetRequest request) {
		return this.tweetRepository.findById(request.getId())
			.switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found with id " + request.getId())))
			.flatMapMany(tweetEntity -> {
				Set<String> mentionTokens = TweetTokenParser.extractMentions(tweetEntity.getContent());
				return Flux.fromIterable(mentionTokens).flatMap(userName -> createMention(userName, tweetEntity));
			})
			.then();
	}

	private Mono<MentionEntity> createMention(String userName, TweetEntity tweetEntity) {
		return this.userService.getUserId(userName)
			.flatMap(userId -> this.mentionRepository
				.save(this.mentionMapper.mapFieldsToEntity(userId, userName, tweetEntity)))
			.onErrorResume(e -> {
				if (e instanceof UserNotFoundException) {
					return Mono.empty();
				}
				return Mono.error(e);
			});
	}

}
