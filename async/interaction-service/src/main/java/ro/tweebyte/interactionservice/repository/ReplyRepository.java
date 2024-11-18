package ro.tweebyte.interactionservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReplyRepository extends JpaRepository<ReplyEntity, UUID> {

    Optional<ReplyEntity> findByIdAndUserId(UUID replyId, UUID userId);

    List<ReplyEntity> findByTweetIdOrderByCreatedAtDesc(UUID tweetId);

    long countByTweetId(UUID tweetId);

    @Query(
        "SELECT NEW ro.tweebyte.interactionservice.model.ReplyDto(r.id, r.userId, r.content, r.createdAt, CAST(COALESCE(COUNT(l), 0) AS long)) " +
        "FROM ReplyEntity r LEFT JOIN LikeEntity l ON r.id = l.likeableId AND l.likeableType = 'REPLY' " +
        "WHERE r.tweetId = :tweetId " +
        "GROUP BY r.id, r.userId, r.content, r.createdAt " +
        "ORDER BY COUNT(l) DESC, r.createdAt DESC"
    )
    Page<ReplyDto> findTopReplyByLikesForTweetId(@Param("tweetId") UUID tweetId, Pageable pageable);

}
