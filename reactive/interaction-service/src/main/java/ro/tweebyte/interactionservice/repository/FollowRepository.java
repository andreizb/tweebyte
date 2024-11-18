package ro.tweebyte.interactionservice.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.FollowEntity;

import java.util.UUID;

@Repository
public interface FollowRepository extends ReactiveCrudRepository<FollowEntity, UUID> {

    @Query("SELECT DISTINCT f.followed_id FROM follows f")
    Flux<UUID> findAllFollowedIds();

    Flux<FollowEntity> findByStatus(String status);

    Flux<FollowEntity> findByFollowerIdAndStatus(UUID userId, String status);

    Flux<FollowEntity> findByFollowedIdAndStatusOrderByCreatedAtDesc(UUID followedId, String status);

    Flux<FollowEntity> findByFollowerIdAndStatusOrderByCreatedAtDesc(UUID followerId, String status);

    Mono<Long> countByFollowedIdAndStatus(UUID followedId, String status);

    Mono<Long> countByFollowerIdAndStatus(UUID followerId, String status);

    Mono<Void> deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

}
