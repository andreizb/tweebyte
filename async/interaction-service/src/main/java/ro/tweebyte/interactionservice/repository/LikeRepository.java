package ro.tweebyte.interactionservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.tweebyte.interactionservice.entity.LikeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, UUID> {

    List<LikeEntity> findByUserIdAndLikeableType(UUID userId, LikeEntity.LikeableType likeableType);

    List<LikeEntity> findByLikeableIdAndLikeableType(UUID likeableId, LikeEntity.LikeableType likeableType);

    long countByLikeableIdAndLikeableType(UUID likeableId, LikeEntity.LikeableType likeableType);

    void deleteByUserIdAndLikeableIdAndLikeableType(UUID userId, UUID likeableId, LikeEntity.LikeableType likeableType);

}
