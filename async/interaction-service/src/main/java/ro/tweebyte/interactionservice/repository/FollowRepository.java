package ro.tweebyte.interactionservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ro.tweebyte.interactionservice.entity.FollowEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, UUID> {

    @Query("SELECT DISTINCT f.followedId FROM FollowEntity f")
    List<UUID> findAllFollowedIds();

    List<FollowEntity> findByStatus(FollowEntity.Status status);

    Page<FollowEntity> findByStatus(FollowEntity.Status status, Pageable pageable);

    List<FollowEntity> findByFollowerIdAndStatus(UUID userId, FollowEntity.Status status);

    List<FollowEntity> findByFollowedIdAndStatusOrderByCreatedAtDesc(UUID followedId, FollowEntity.Status status);

    List<FollowEntity> findByFollowerIdAndStatusOrderByCreatedAtDesc(UUID followerId, FollowEntity.Status status);

    Page<FollowEntity> findByFollowedIdAndStatusOrderByCreatedAtDesc(UUID followedId, FollowEntity.Status status, Pageable pageable);

    Page<FollowEntity> findByFollowerIdAndStatusOrderByCreatedAtDesc(UUID followerId, FollowEntity.Status status, Pageable pageable);

    long countByFollowedIdAndStatus(UUID followedId, FollowEntity.Status status);

    long countByFollowerIdAndStatus(UUID followerId, FollowEntity.Status status);

    void deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

}
