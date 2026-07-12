/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.model.TweetMention;

@Repository
public interface MentionRepository extends JpaRepository<MentionEntity, UUID> {

	@Query("SELECT m FROM MentionEntity m WHERE m.tweetEntity.id = :tweetId ORDER BY m.text, m.id")
	List<MentionEntity> findMentionsByTweetId(@Param("tweetId") UUID tweetId);

	// Batched lookup: one query resolves the mentions for a whole page of tweets,
	// replacing the per-tweet findMentionsByTweetId fan-out. Selects the tweet_id
	// FK as a scalar so each row carries its tweet id without initialising the
	// eager @ManyToOne back-reference.
	@Query("SELECT new ro.tweebyte.tweetservice.model.TweetMention(m.tweetEntity.id, m.id, m.userId, m.text) "
			+ "FROM MentionEntity m WHERE m.tweetEntity.id IN :tweetIds ORDER BY m.tweetEntity.id, m.text, m.id")
	List<TweetMention> findMentionsByTweetIdIn(@Param("tweetIds") Collection<UUID> tweetIds);

}
