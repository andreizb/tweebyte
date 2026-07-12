/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ro.tweebyte.interactionservice.entity.FollowEntity;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, UUID> {

	@Query("SELECT DISTINCT f.followedId FROM FollowEntity f")
	List<UUID> findAllFollowedIds();

	Page<FollowEntity> findByStatus(FollowEntity.Status status, Pageable pageable);

	List<FollowEntity> findByFollowerIdAndStatus(UUID userId, FollowEntity.Status status);

	List<FollowEntity> findByFollowerIdInAndStatus(Collection<UUID> followerIds, FollowEntity.Status status);

	List<FollowEntity> findByFollowedIdAndStatusOrderByCreatedAtDesc(UUID followedId, FollowEntity.Status status);

	long countByFollowedIdAndStatus(UUID followedId, FollowEntity.Status status);

	long countByFollowerIdAndStatus(UUID followerId, FollowEntity.Status status);

	// Collapsed follower+following counts in ONE round-trip for the user-profile read (replaces
	// the two separate COUNT queries above). Native query: each scalar subselect keeps its own
	// index on followed_id / follower_id; status is the stored string ('ACCEPTED').
	@Query(value = "SELECT (SELECT count(*) FROM follows WHERE followed_id = :userId AND status = :status) AS followers, "
			+ "(SELECT count(*) FROM follows WHERE follower_id = :userId AND status = :status) AS following",
			nativeQuery = true)
	FollowCountsRow countFollowersAndFollowing(@Param("userId") UUID userId, @Param("status") String status);

	// Resolve the edge before an unfollow so the count delta only fires for an edge that was
	// actually counted (an ACCEPTED edge). Mirrors reactive's findByFollowerIdAndFollowedId.
	Optional<FollowEntity> findByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

	// A derived delete loads the row and then removes it, so it needs an active
	// transaction. The unfollow path in FollowService runs asynchronously with no
	// service-level transaction, so the transaction is scoped here instead. Keeping it
	// off the service preserves the evict-after-commit ordering, where the Redis evict
	// runs only after this delete commits.
	@Transactional
	void deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

}
