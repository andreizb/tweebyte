/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetHashtag;
import ro.tweebyte.tweetservice.model.TweetRelation;

@Repository
public interface HashtagRepository extends ReactiveCrudRepository<HashtagEntity, UUID>, HashtagRepositoryCustom {

	Mono<HashtagEntity> findByText(String text);

	@Query("SELECT * FROM hashtags WHERE text IN (:texts)")
	Flux<HashtagEntity> findByTextIn(Collection<String> texts);

	@Query("SELECT h.id as id, h.text as text, COUNT(th.tweet_id) as count " + "FROM hashtags h "
			+ "JOIN tweet_hashtag th ON h.id = th.hashtag_id " + "GROUP BY h.id, h.text "
			+ "ORDER BY COUNT(th.tweet_id) DESC, h.id")
	Flux<HashtagDto> findPopularHashtags();

	@Query("SELECT h.* FROM hashtags h " + "JOIN tweet_hashtag th ON h.id = th.hashtag_id "
			+ "WHERE th.tweet_id = :tweetId " + "ORDER BY h.text, h.id")
	Flux<HashtagEntity> findHashtagsByTweetId(UUID tweetId);

	@Query("SELECT th.tweet_id AS tweet_id, h.id AS id, h.text AS text FROM hashtags h "
			+ "JOIN tweet_hashtag th ON h.id = th.hashtag_id " + "WHERE th.tweet_id IN (:tweetIds) "
			+ "ORDER BY th.tweet_id, h.text, h.id")
	Flux<TweetHashtag> findHashtagsByTweetIdIn(Collection<UUID> tweetIds);

	// Combined relation read for a whole page: ONE UNION over the hashtag join and the mentions
	// table returns every hashtag ('H') and mention ('M') of the given tweets in a single query —
	// one r2dbc connection acquire for the page's relations instead of one per family. The caller
	// splits the rows back per tweet by their kind discriminator. user_id is null on hashtag rows.
	@Query("SELECT th.tweet_id AS tweet_id, 'H' AS kind, h.id AS id, CAST(NULL AS uuid) AS user_id, h.text AS text "
			+ "FROM hashtags h JOIN tweet_hashtag th ON h.id = th.hashtag_id WHERE th.tweet_id IN (:tweetIds) "
			+ "UNION ALL "
			+ "SELECT m.tweet_id AS tweet_id, 'M' AS kind, m.id AS id, m.user_id AS user_id, m.text AS text "
			+ "FROM mentions m WHERE m.tweet_id IN (:tweetIds) "
			+ "ORDER BY tweet_id, kind, text, id")
	Flux<TweetRelation> findRelationsByTweetIdIn(Collection<UUID> tweetIds);

}
