/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagProjection;
import ro.tweebyte.tweetservice.model.TweetHashtag;

@Repository
public interface HashtagRepository extends JpaRepository<HashtagEntity, UUID> {

	Optional<HashtagEntity> findByText(String text);

	List<HashtagEntity> findByTextIn(Collection<String> texts);

	@Query(value = "SELECT h.* FROM hashtags h " + "INNER JOIN tweet_hashtag th ON h.id = th.hashtag_id "
			+ "WHERE th.tweet_id = :tweetId " + "ORDER BY h.text, h.id", nativeQuery = true)
	List<HashtagEntity> findHashtagsByTweetId(UUID tweetId);

	// Batched lookup: one join over the tweet_hashtag table resolves the
	// hashtags for a whole page of tweets, replacing the per-tweet
	// findHashtagsByTweetId fan-out. Each row carries its tweet id so the
	// service can group hashtags back per tweet.
	@Query("SELECT new ro.tweebyte.tweetservice.model.TweetHashtag(t.id, h.id, h.text) "
			+ "FROM TweetEntity t JOIN t.hashtags h WHERE t.id IN :tweetIds ORDER BY t.id, h.text, h.id")
	List<TweetHashtag> findHashtagsByTweetIdIn(@Param("tweetIds") Collection<UUID> tweetIds);

	// Combined relation read for a whole page: ONE UNION over the hashtag join and the mentions
	// table returns every hashtag ('H') and mention ('M') of the given tweets in a single query —
	// one Hikari connection acquire for the page's relations instead of one per family. JPQL has no
	// UNION, so this is native; the service maps the positional rows (tweet_id, kind, id, user_id,
	// text) into TweetRelation and splits them per tweet by kind. user_id is null on hashtag rows.
	@Query(value = "SELECT th.tweet_id AS tweet_id, 'H' AS kind, h.id AS id, CAST(NULL AS uuid) AS user_id, h.text AS text "
			+ "FROM hashtags h JOIN tweet_hashtag th ON h.id = th.hashtag_id WHERE th.tweet_id IN (:tweetIds) "
			+ "UNION ALL "
			+ "SELECT m.tweet_id AS tweet_id, 'M' AS kind, m.id AS id, m.user_id AS user_id, m.text AS text "
			+ "FROM mentions m WHERE m.tweet_id IN (:tweetIds) "
			+ "ORDER BY tweet_id, kind, text, id", nativeQuery = true)
	List<Object[]> findRelationRowsByTweetIdIn(@Param("tweetIds") Collection<UUID> tweetIds);

	// No LIMIT — returns every hashtag ordered by usage, matching reactive's
	// findPopularHashtags() which applies no pagination either.
	@Query(value = "SELECT h.id as id, h.text as text, COUNT(th.tweet_id) as count " + "FROM hashtags h "
			+ "INNER JOIN tweet_hashtag th ON h.id = th.hashtag_id " + "GROUP BY h.id, h.text "
			+ "ORDER BY count DESC, h.id", nativeQuery = true)
	List<HashtagProjection> findPopularHashtags();

}
