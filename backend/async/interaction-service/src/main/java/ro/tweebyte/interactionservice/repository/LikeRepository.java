/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.TweetCount;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, UUID> {

	@Query("SELECT l FROM LikeEntity l WHERE l.userId = :userId AND l.likeableType = :likeableType "
			+ "ORDER BY l.id LIMIT :limit OFFSET :offset")
	List<LikeEntity> findByUserIdAndLikeableType(@Param("userId") UUID userId,
			@Param("likeableType") LikeEntity.LikeableType likeableType, @Param("limit") int limit,
			@Param("offset") int offset);

	// Batched count: one GROUP BY resolves the like counts for a whole page of
	// tweets, replacing the per-tweet countByLikeableIdAndLikeableType fan-out.
	@Query("SELECT new ro.tweebyte.interactionservice.model.TweetCount(l.likeableId, CAST(COUNT(l) AS long)) "
			+ "FROM LikeEntity l WHERE l.likeableId IN :tweetIds AND l.likeableType = :likeableType "
			+ "GROUP BY l.likeableId")
	List<TweetCount> countByLikeableIdInAndLikeableType(@Param("tweetIds") Collection<UUID> tweetIds,
			@Param("likeableType") LikeEntity.LikeableType likeableType);

	Optional<LikeEntity> findByUserIdAndLikeableIdAndLikeableType(UUID userId, UUID likeableId,
			LikeEntity.LikeableType likeableType);

	@Query("SELECT l FROM LikeEntity l WHERE l.likeableId = :likeableId AND l.likeableType = :likeableType "
			+ "ORDER BY l.id LIMIT :limit OFFSET :offset")
	List<LikeEntity> findByLikeableIdAndLikeableType(@Param("likeableId") UUID likeableId,
			@Param("likeableType") LikeEntity.LikeableType likeableType, @Param("limit") int limit,
			@Param("offset") int offset);

	long countByLikeableIdAndLikeableType(UUID likeableId, LikeEntity.LikeableType likeableType);

	void deleteByUserIdAndLikeableIdAndLikeableType(UUID userId, UUID likeableId, LikeEntity.LikeableType likeableType);

}
