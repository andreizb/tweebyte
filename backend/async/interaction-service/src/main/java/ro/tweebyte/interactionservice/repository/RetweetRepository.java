/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.TweetCount;

@Repository
public interface RetweetRepository extends JpaRepository<RetweetEntity, UUID> {

	@Query("SELECT rt FROM RetweetEntity rt WHERE rt.retweeterId = :retweeterId "
			+ "ORDER BY rt.createdAt DESC, rt.id LIMIT :limit OFFSET :offset")
	List<RetweetEntity> findByRetweeterId(@Param("retweeterId") UUID userId, @Param("limit") int limit,
			@Param("offset") int offset);

	@Query("SELECT rt FROM RetweetEntity rt WHERE rt.originalTweetId = :originalTweetId "
			+ "ORDER BY rt.createdAt DESC, rt.id LIMIT :limit OFFSET :offset")
	List<RetweetEntity> findByOriginalTweetId(@Param("originalTweetId") UUID tweetId, @Param("limit") int limit,
			@Param("offset") int offset);

	long countByOriginalTweetId(UUID tweetId);

	// Batched count: one GROUP BY resolves the retweet counts for a whole page of
	// tweets, replacing the per-tweet countByOriginalTweetId fan-out.
	@Query("SELECT new ro.tweebyte.interactionservice.model.TweetCount(rt.originalTweetId, CAST(COUNT(rt) AS long)) "
			+ "FROM RetweetEntity rt WHERE rt.originalTweetId IN :tweetIds GROUP BY rt.originalTweetId")
	List<TweetCount> countByOriginalTweetIdIn(@Param("tweetIds") Collection<UUID> tweetIds);

	@Query(value = "SELECT DISTINCT unnest(media_ids) AS media_id FROM retweets WHERE media_ids IS NOT NULL",
			nativeQuery = true)
	List<UUID> findReferencedMediaIds();

}
