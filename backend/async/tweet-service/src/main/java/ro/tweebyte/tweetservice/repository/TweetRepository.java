/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import ro.tweebyte.tweetservice.entity.TweetEntity;

@Repository
public interface TweetRepository extends JpaRepository<TweetEntity, UUID> {

	@NonNull
	@EntityGraph(attributePaths = { "mentions", "hashtags" })
	Optional<TweetEntity> findById(@NonNull UUID id);

	@EntityGraph(attributePaths = { "mentions", "hashtags" })
	Optional<TweetEntity> findByIdAndUserId(UUID id, UUID userId);

	List<TweetEntity> findByUserId(UUID userId, Pageable pageable);

	// Unpaged feed for the recommender's user-summary: every tweet a user has
	// posted, newest first with id as a stable tiebreaker (same order as the
	// paginated PAGE_SORT). The recommender scores across the whole set, so it
	// must not be capped by a page size.
	List<TweetEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

	@Query(value = "SELECT * FROM tweets WHERE :searchTerm <% content ORDER BY word_similarity(:searchTerm, content) DESC, created_at DESC, id DESC LIMIT :limit OFFSET :offset",
			nativeQuery = true)
	List<TweetEntity> findBySimilarity(String searchTerm, int limit, int offset);

	@Query(value = "SELECT t.* FROM tweets t INNER JOIN tweet_hashtag th ON t.id = th.tweet_id INNER JOIN hashtags h ON th.hashtag_id = h.id WHERE h.text = :hashtag ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset",
			nativeQuery = true)
	List<TweetEntity> findByHashtag(String hashtag, int limit, int offset);

	List<TweetEntity> findByUserIdIn(List<UUID> userIds, Pageable pageable);

	// Distinct media_assets ids still referenced by any tweet. user-service's
	// stale-media GC unions this with its other reference sources so a tweet's
	// media is never swept (cross-DB, so it can't see these refs locally).
	@Query(value = "SELECT DISTINCT unnest(media_ids) AS media_id FROM tweets WHERE media_ids IS NOT NULL",
			nativeQuery = true)
	List<UUID> findReferencedMediaIds();

}
