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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.HashtagProjection;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class HashtagRepositoryTests {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private HashtagRepository hashtagRepository;

	@Test
	void findByText() {
		String text = "example";
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setText(text);

		this.entityManager.persist(hashtagEntity);
		this.entityManager.flush();

		Optional<HashtagEntity> foundHashtag = this.hashtagRepository.findByText(text);

		assertThat(foundHashtag).isPresent();
		assertThat(foundHashtag.get().getText()).isEqualTo(text);
	}

	@Test
	void findPopularHashtags() {
		HashtagEntity hashtag1 = new HashtagEntity();
		hashtag1.setText("hashtag1");
		this.entityManager.persist(hashtag1);

		HashtagEntity hashtag2 = new HashtagEntity();
		hashtag2.setText("hashtag2");
		this.entityManager.persist(hashtag2);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setHashtags(Set.of(hashtag1, hashtag2));
		tweetEntity.setContent("asdf");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		tweetEntity.setUserId(UUID.randomUUID());
		this.entityManager.persist(tweetEntity);

		List<HashtagProjection> popularHashtags = this.hashtagRepository.findPopularHashtags();

		assertThat(popularHashtags).hasSize(2);
	}

}
