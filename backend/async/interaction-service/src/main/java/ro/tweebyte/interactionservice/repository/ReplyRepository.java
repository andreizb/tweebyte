/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TopReply;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetInteractionRow;

@Repository
public interface ReplyRepository extends JpaRepository<ReplyEntity, UUID> {

	@Query("SELECT r FROM ReplyEntity r WHERE r.tweetId = :tweetId "
			+ "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit OFFSET :offset")
	List<ReplyEntity> findByTweetIdOrderByCreatedAtDescIdDesc(@Param("tweetId") UUID tweetId, @Param("limit") int limit,
			@Param("offset") int offset);

	long countByTweetId(UUID tweetId);

	@Query("SELECT NEW ro.tweebyte.interactionservice.model.ReplyDto(r.id, r.userId, r.content, r.createdAt, CAST(COALESCE(COUNT(l), 0) AS long)) "
			+ "FROM ReplyEntity r LEFT JOIN LikeEntity l ON r.id = l.likeableId AND l.likeableType = 'REPLY' "
			+ "WHERE r.tweetId = :tweetId " + "GROUP BY r.id, r.userId, r.content, r.createdAt "
			+ "ORDER BY COUNT(l) DESC, r.createdAt DESC, r.id")
	Page<ReplyDto> findTopReplyByLikesForTweetId(@Param("tweetId") UUID tweetId, Pageable pageable);

	// Batched count: one GROUP BY resolves the reply counts for a whole page of
	// tweets, replacing the per-tweet countByTweetId fan-out.
	@Query("SELECT new ro.tweebyte.interactionservice.model.TweetCount(r.tweetId, CAST(COUNT(r) AS long)) "
			+ "FROM ReplyEntity r WHERE r.tweetId IN :tweetIds GROUP BY r.tweetId")
	List<TweetCount> countByTweetIdIn(@Param("tweetIds") Collection<UUID> tweetIds);

	// Batched top-reply: one query resolves every reply (with its like count) for
	// a whole page of tweets, ordered so the top reply per tweet is the first row
	// in its tweet's group. The service picks the first row per tweetId — the
	// JPQL equivalent of the reactive stack's ROW_NUMBER() window pick, without a
	// window function (JPQL has none).
	@Query("SELECT new ro.tweebyte.interactionservice.model.TopReply("
			+ "r.tweetId, r.id, r.userId, r.content, r.createdAt, CAST(COALESCE(COUNT(l), 0) AS long)) "
			+ "FROM ReplyEntity r LEFT JOIN LikeEntity l ON r.id = l.likeableId AND l.likeableType = 'REPLY' "
			+ "WHERE r.tweetId IN :tweetIds " + "GROUP BY r.tweetId, r.id, r.userId, r.content, r.createdAt "
			+ "ORDER BY r.tweetId, COUNT(l) DESC, r.createdAt DESC, r.id")
	List<TopReply> findRepliesByLikesForTweetIds(@Param("tweetIds") Collection<UUID> tweetIds);

	// Consolidated per-tweet interaction read for a page (mirrors the reactive R2DBC query): the
	// three counts (correlated COUNT subqueries) and the top reply (a LATERAL ranked LIMIT 1, same
	// like-count/recency order as findRepliesByLikesForTweetIds) in ONE native query — one Hikari
	// connection acquire instead of four. Quoted camelCase aliases map to the projection getters;
	// unnest yields one row per requested tweet, so a tweet with no rows still returns zeros and a
	// null top reply.
	@Query(value = "SELECT t.id AS \"tweetId\", "
			+ "(SELECT COUNT(*) FROM likes WHERE likeable_id = t.id AND likeable_type = 'TWEET') AS \"likeCount\", "
			+ "(SELECT COUNT(*) FROM replies WHERE tweet_id = t.id) AS \"replyCount\", "
			+ "(SELECT COUNT(*) FROM retweets WHERE original_tweet_id = t.id) AS \"retweetCount\", "
			+ "tr.id AS \"topReplyId\", tr.user_id AS \"topReplyUserId\", tr.content AS \"topReplyContent\", "
			+ "tr.created_at AS \"topReplyCreatedAt\", tr.like_count AS \"topReplyLikeCount\" "
			+ "FROM unnest(:tweetIds) AS t(id) "
			+ "LEFT JOIN LATERAL ("
			+ "SELECT r.id, r.user_id, r.content, r.created_at, "
			+ "(SELECT COUNT(*) FROM likes WHERE likeable_id = r.id AND likeable_type = 'REPLY') AS like_count "
			+ "FROM replies r WHERE r.tweet_id = t.id "
			+ "ORDER BY like_count DESC, r.created_at DESC, r.id LIMIT 1) tr ON true", nativeQuery = true)
	List<TweetInteractionRow> findInteractionsForTweets(@Param("tweetIds") UUID[] tweetIds);

	@Query(value = "SELECT DISTINCT unnest(media_ids) AS media_id FROM replies WHERE media_ids IS NOT NULL",
			nativeQuery = true)
	List<UUID> findReferencedMediaIds();

}
