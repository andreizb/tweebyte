/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.TopReply;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetInteractionRow;

@Repository
public interface ReplyRepository extends ReactiveCrudRepository<ReplyEntity, UUID> {

	@Query("SELECT * FROM replies WHERE tweet_id = :tweetId "
			+ "ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
	Flux<ReplyEntity> findByTweetIdOrderByCreatedAtDescIdDesc(UUID tweetId, int limit, int offset);

	Mono<Long> countByTweetId(UUID tweetId);

	@Query("SELECT tweet_id, COUNT(*) AS total FROM replies WHERE tweet_id IN (:tweetIds) GROUP BY tweet_id")
	Flux<TweetCount> countByTweetIdIn(Collection<UUID> tweetIds);

	@Query("SELECT r.tweet_id, r.id, r.user_id, r.content, r.created_at, COUNT(l.id) AS like_count FROM replies r "
			+ "LEFT JOIN likes l ON r.id = l.likeable_id AND l.likeable_type = 'REPLY' "
			+ "WHERE r.tweet_id = :tweetId " + "GROUP BY r.tweet_id, r.id, r.user_id, r.content, r.created_at "
			+ "ORDER BY like_count DESC, r.created_at DESC, r.id " + "LIMIT 1")
	Flux<TopReply> findTopReplyByLikesForTweetId(UUID tweetId);

	// Batched top-reply-per-tweet: rank replies within each tweet by likes (then
	// recency) via a window function and keep rank 1, so one query returns every
	// page tweet's top reply instead of a per-tweet round trip.
	@Query("SELECT ranked.tweet_id, ranked.id, ranked.user_id, ranked.content, ranked.created_at, ranked.like_count FROM ("
			+ "SELECT r.tweet_id, r.id, r.user_id, r.content, r.created_at, COUNT(l.id) AS like_count, "
			+ "ROW_NUMBER() OVER (PARTITION BY r.tweet_id ORDER BY COUNT(l.id) DESC, r.created_at DESC, r.id) AS rn "
			+ "FROM replies r " + "LEFT JOIN likes l ON r.id = l.likeable_id AND l.likeable_type = 'REPLY' "
			+ "WHERE r.tweet_id IN (:tweetIds) "
			+ "GROUP BY r.tweet_id, r.id, r.user_id, r.content, r.created_at) ranked " + "WHERE ranked.rn = 1")
	Flux<TopReply> findTopRepliesByLikesForTweetIds(Collection<UUID> tweetIds);

	// Consolidated per-tweet interaction read for a page: the three counts (correlated COUNT
	// subqueries) and the top reply (a LATERAL ranked LIMIT 1, same like-count/recency order as
	// findTopRepliesByLikesForTweetIds) in ONE query — one r2dbc connection acquire instead of
	// four. unnest yields one row per requested tweet, so a tweet with no rows still returns zeros
	// and a null top reply.
	@Query("SELECT t.id AS tweet_id, "
			+ "(SELECT COUNT(*) FROM likes WHERE likeable_id = t.id AND likeable_type = 'TWEET') AS like_count, "
			+ "(SELECT COUNT(*) FROM replies WHERE tweet_id = t.id) AS reply_count, "
			+ "(SELECT COUNT(*) FROM retweets WHERE original_tweet_id = t.id) AS retweet_count, "
			+ "tr.id AS top_reply_id, tr.user_id AS top_reply_user_id, tr.content AS top_reply_content, "
			+ "tr.created_at AS top_reply_created_at, tr.like_count AS top_reply_like_count "
			+ "FROM unnest(:tweetIds) AS t(id) "
			+ "LEFT JOIN LATERAL ("
			+ "SELECT r.id, r.user_id, r.content, r.created_at, "
			+ "(SELECT COUNT(*) FROM likes WHERE likeable_id = r.id AND likeable_type = 'REPLY') AS like_count "
			+ "FROM replies r WHERE r.tweet_id = t.id "
			+ "ORDER BY like_count DESC, r.created_at DESC, r.id LIMIT 1) tr ON true")
	Flux<TweetInteractionRow> findInteractionsForTweets(UUID[] tweetIds);

	@Query("SELECT DISTINCT unnest(media_ids) AS media_id FROM replies WHERE media_ids IS NOT NULL")
	Flux<UUID> findReferencedMediaIds();

}
