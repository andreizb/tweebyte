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

import ro.tweebyte.interactionservice.entity.FollowEntity;

@Repository
public interface FollowRepository extends ReactiveCrudRepository<FollowEntity, UUID> {

	@Query("SELECT DISTINCT f.followed_id FROM follows f")
	Flux<UUID> findAllFollowedIds();

	Flux<FollowEntity> findByStatus(String status);

	Flux<FollowEntity> findByFollowerIdAndStatus(UUID followerId, String status);

	Flux<FollowEntity> findByFollowerIdInAndStatus(Collection<UUID> followerIds, String status);

	Flux<FollowEntity> findByFollowedIdAndStatusOrderByCreatedAtDesc(UUID followedId, String status);

	Mono<Long> countByFollowedIdAndStatus(UUID followedId, String status);

	Mono<Long> countByFollowerIdAndStatus(UUID followerId, String status);

	// Collapsed follower+following counts in ONE round-trip for the user-profile read
	// (replaces the two separate COUNT queries above). Each scalar subselect keeps its own
	// index on followed_id / follower_id; the status filter matches the per-type methods.
	@Query("SELECT (SELECT count(*) FROM follows WHERE followed_id = :userId AND status = :status) AS followers, "
			+ "(SELECT count(*) FROM follows WHERE follower_id = :userId AND status = :status) AS following")
	Mono<FollowCountsRow> countFollowersAndFollowing(UUID userId, String status);

	Mono<FollowEntity> findByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

	Mono<Void> deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

}
