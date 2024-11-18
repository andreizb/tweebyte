package ro.tweebyte.interactionservice.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.ReplyEntity;

import java.util.UUID;

@Repository
public interface ReplyRepository extends ReactiveCrudRepository<ReplyEntity, UUID> {

    Mono<ReplyEntity> findByIdAndUserId(UUID replyId, UUID userId);

    Flux<ReplyEntity> findByTweetIdOrderByCreatedAtDesc(UUID tweetId);

    Mono<Long> countByTweetId(UUID tweetId);

    @Query("SELECT r.*, COUNT(l.id) AS like_count FROM replies r " +
        "LEFT JOIN likes l ON r.id = l.likeable_id AND l.likeable_type = 'REPLY' " +
        "WHERE r.tweet_id = :tweetId " +
        "GROUP BY r.id " +
        "ORDER BY like_count DESC, r.created_at DESC " +
        "LIMIT 1")
    Flux<ReplyEntity> findTopReplyByLikesForTweetId(UUID tweetId);

}
