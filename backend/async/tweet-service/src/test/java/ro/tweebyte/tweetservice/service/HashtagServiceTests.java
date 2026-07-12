/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.HashtagProjection;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class HashtagServiceTests {

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private HashtagMapper hashtagMapper;

	@InjectMocks
	private HashtagService hashtagService;

	@Test
	void testHandleTweetCreationHashtags() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(UUID.randomUUID());

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent("This is a #test tweet.");
		tweetEntity.setHashtags(new HashSet<>());

		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setText("#test");

		given(this.tweetRepository.findById(any(UUID.class))).willReturn(Optional.of(tweetEntity));
		given(this.hashtagMapper.mapTextToEntity(any(String.class))).willReturn(hashtagEntity);

		this.hashtagService.handleTweetCreationHashtags(request);

		assertThat(tweetEntity.getHashtags()).isNotEmpty();
	}

	@Test
	void testHandleTweetCreationHashtagsWithTweetNotFound() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(UUID.randomUUID());

		given(this.tweetRepository.findById(any(UUID.class))).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.hashtagService.handleTweetCreationHashtags(request))
			.isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testComputePopularHashtags() throws ExecutionException, InterruptedException {
		List<HashtagProjection> popularHashtags = Collections.emptyList();
		given(this.hashtagRepository.findPopularHashtags()).willReturn(popularHashtags);

		CompletableFuture<List<HashtagDto>> result = this.hashtagService.computePopularHashtags();

		assertThat(result).isNotNull();
		assertThat(result.get()).isEqualTo(Collections.emptyList());
	}

	@Test
	void testComputePopularHashtagsMapsProjectionToDto() throws ExecutionException, InterruptedException {
		UUID id = UUID.randomUUID();
		HashtagProjection projection = new HashtagProjection() {
			@Override
			public UUID getId() {
				return id;
			}

			@Override
			public String getText() {
				return "popular";
			}

			@Override
			public Long getCount() {
				return 7L;
			}
		};
		given(this.hashtagRepository.findPopularHashtags()).willReturn(List.of(projection));

		List<HashtagDto> result = this.hashtagService.computePopularHashtags().get();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(id);
		assertThat(result.get(0).getText()).isEqualTo("popular");
		assertThat(result.get(0).getCount()).isEqualTo(7L);
	}

}
