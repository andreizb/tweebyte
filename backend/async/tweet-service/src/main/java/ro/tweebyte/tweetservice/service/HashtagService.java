/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.HashtagProjection;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

@Service
@RequiredArgsConstructor
public class HashtagService {

	private final HashtagRepository hashtagRepository;

	private final TweetRepository tweetRepository;

	private final HashtagMapper hashtagMapper;

	@Transactional
	public void handleTweetCreationHashtags(TweetRequest request) {
		TweetEntity tweetEntity = this.tweetRepository.findById(request.getId())
			.orElseThrow(() -> new TweetNotFoundException("Tweet not found with id " + request.getId()));

		Set<HashtagEntity> hashtags = TweetTokenParser.extractHashtags(tweetEntity.getContent())
			.stream()
			.map(this::findOrCreateHashtag)
			.collect(Collectors.toSet());

		tweetEntity.getHashtags().clear();
		tweetEntity.getHashtags().addAll(hashtags);
	}

	@Async
	public CompletableFuture<List<HashtagDto>> computePopularHashtags() {
		List<HashtagDto> hashtags = this.hashtagRepository.findPopularHashtags()
			.stream()
			.map(this::mapProjectionToDto)
			.toList();
		return CompletableFuture.completedFuture(hashtags);
	}

	private HashtagDto mapProjectionToDto(HashtagProjection projection) {
		return HashtagDto.builder()
			.id(projection.getId())
			.text(projection.getText())
			.count(projection.getCount())
			.build();
	}

	private HashtagEntity findOrCreateHashtag(String text) {
		return this.hashtagRepository.findByText(text).orElseGet(() -> this.hashtagMapper.mapTextToEntity(text));
	}

}
