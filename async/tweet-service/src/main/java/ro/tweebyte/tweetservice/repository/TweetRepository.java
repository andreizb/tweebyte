package ro.tweebyte.tweetservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.lang.NonNullApi;
import org.springframework.stereotype.Repository;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TweetRepository extends JpaRepository<TweetEntity, UUID> {

    @NonNull
    @EntityGraph(attributePaths = {"mentions", "hashtags"})
    Optional<TweetEntity> findById(@NonNull UUID id);

    Optional<TweetEntity> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"mentions", "hashtags"})
    List<TweetEntity> findByUserId(UUID userId);

    @Query(value = "SELECT * FROM tweets WHERE content ILIKE :searchTerm", nativeQuery = true)
    List<TweetEntity> findBySimilarity(String searchTerm);

    @Query(value = "SELECT t.* FROM tweets t INNER JOIN tweet_hashtag th ON t.id = th.tweet_id INNER JOIN hashtags h ON th.hashtag_id = h.id WHERE h.text = :hashtag", nativeQuery = true)
    List<TweetEntity> findByHashtag(String hashtag);

    @EntityGraph(attributePaths = {"mentions", "hashtags"})
    List<TweetEntity> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds);

}
