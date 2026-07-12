/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.entity.TweetHashtagEntity;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HashtagServiceTests {

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

	private TweetCreationRequest tweetRequest;

	private TweetEntity tweetEntity;

	@BeforeEach
	void setup() {
		this.tweetRequest = new TweetCreationRequest();
		this.tweetRequest.setId(UUID.randomUUID());
		this.tweetRequest.setContent("Hello #hashtag1 #hashtag2");

		this.tweetEntity = new TweetEntity();
		this.tweetEntity.setId(this.tweetRequest.getId());
		this.tweetEntity.setContent(this.tweetRequest.getContent());
	}

	@Test
	void handleTweetCreationHashtags_Success() {
		given(this.tweetRepository.findById(this.tweetRequest.getId())).willReturn(Mono.just(this.tweetEntity));
		given(this.hashtagRepository.findByText(any())).willReturn(Mono.empty());
		given(this.hashtagMapper.mapTextToEntity(any())).willReturn(new HashtagEntity());
		given(this.hashtagRepository.save(any())).willReturn(Mono.just(new HashtagEntity()));
		given(this.tweetHashtagRepository.save(any())).willReturn(Mono.just(new TweetHashtagEntity()));

		StepVerifier.create(this.hashtagService.handleTweetCreationHashtags(this.tweetRequest)).verifyComplete();

		verify(this.tweetRepository).findById(this.tweetRequest.getId());
		verify(this.hashtagRepository, times(2)).findByText(any());
		verify(this.hashtagRepository, times(2)).save(any());
		verify(this.tweetHashtagRepository, times(2)).save(any());
	}

	@Test
	void handleTweetCreationHashtags_TweetNotFound() {
		given(this.tweetRepository.findById(this.tweetRequest.getId())).willReturn(Mono.empty());

		StepVerifier.create(this.hashtagService.handleTweetCreationHashtags(this.tweetRequest))
			.expectError(RuntimeException.class)
			.verify();

		verify(this.tweetRepository).findById(this.tweetRequest.getId());
		verifyNoInteractions(this.hashtagRepository);
	}

	@Test
	void computePopularHashtags_Success() {
		HashtagDto hashtagDto = new HashtagDto();
		hashtagDto.setText("popular");

		given(this.hashtagRepository.findPopularHashtags()).willReturn(Flux.just(hashtagDto));

		StepVerifier.create(this.hashtagService.computePopularHashtags())
			.expectNextMatches(hashtag -> hashtag.getText().equals("popular"))
			.verifyComplete();

		verify(this.hashtagRepository).findPopularHashtags();
	}

}
