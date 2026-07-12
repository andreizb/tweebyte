/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class TweetRepositoryTests {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private TweetRepository tweetRepository;

	@Test
	void findById() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("asdf");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		tweetEntity.setUserId(UUID.randomUUID());
		UUID tweetId = this.entityManager.persist(tweetEntity).getId();
		this.entityManager.flush();

		Optional<TweetEntity> foundTweet = this.tweetRepository.findById(tweetId);

		assertThat(foundTweet).isPresent();
		assertThat(foundTweet.get().getId()).isEqualTo(tweetId);
	}

	@Test
	void findByIdAndUserId() {
		UUID userId = UUID.randomUUID();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setUserId(userId);
		tweetEntity.setContent("asdf");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		UUID tweetId = this.entityManager.persist(tweetEntity).getId();
		this.entityManager.flush();

		Optional<TweetEntity> foundTweet = this.tweetRepository.findByIdAndUserId(tweetId, userId);

		assertThat(foundTweet).isPresent();
		assertThat(foundTweet.get().getId()).isEqualTo(tweetId);
		assertThat(foundTweet.get().getUserId()).isEqualTo(userId);
	}

	@Test
	void findByUserId() {
		UUID userId = UUID.randomUUID();
		TweetEntity tweet1 = new TweetEntity();
		tweet1.setId(UUID.randomUUID());
		tweet1.setUserId(userId);
		tweet1.setContent("asdf");
		tweet1.setCreatedAt(LocalDateTime.now());
		this.entityManager.persist(tweet1);

		TweetEntity tweet2 = new TweetEntity();
		tweet2.setId(UUID.randomUUID());
		tweet2.setUserId(userId);
		tweet2.setContent("asdf");
		tweet2.setCreatedAt(LocalDateTime.now());
		this.entityManager.persist(tweet2);

		this.entityManager.flush();

		List<TweetEntity> foundTweets = this.tweetRepository.findByUserId(userId, PageRequest.of(0, 10));

		assertThat(foundTweets).hasSize(2);
	}

	@Test
	void findByHashtag() {
		String hashtag = "exampleHashtag";

		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setText(hashtag);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setUserId(UUID.randomUUID());
		tweetEntity.setContent("asdf");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		tweetEntity.setHashtags(Set.of(hashtagEntity));
		UUID tweetId = this.entityManager.persist(tweetEntity).getId();
		this.entityManager.flush();

		List<TweetEntity> foundTweets = this.tweetRepository.findByHashtag(hashtag, 10, 0);

		assertThat(foundTweets).hasSize(1);
		assertThat(foundTweets.get(0).getId()).isEqualTo(tweetId);
	}

	@Test
	void findByUserIdIn() {
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();
		TweetEntity tweet1 = new TweetEntity();
		tweet1.setId(UUID.randomUUID());
		tweet1.setUserId(userId1);
		tweet1.setContent("asdf");
		tweet1.setCreatedAt(LocalDateTime.now());
		this.entityManager.persist(tweet1);

		TweetEntity tweet2 = new TweetEntity();
		tweet2.setId(UUID.randomUUID());
		tweet2.setUserId(userId2);
		tweet2.setContent("asdf");
		tweet2.setCreatedAt(LocalDateTime.now().minusMinutes(1));
		this.entityManager.persist(tweet2);

		this.entityManager.flush();

		List<UUID> userIds = List.of(userId1, userId2);
		Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		List<TweetEntity> foundTweets = this.tweetRepository.findByUserIdIn(userIds, pageable);

		assertThat(foundTweets).hasSize(2);
		assertThat(foundTweets.get(0).getCreatedAt().isAfter(foundTweets.get(1).getCreatedAt())).isTrue();
	}

}
