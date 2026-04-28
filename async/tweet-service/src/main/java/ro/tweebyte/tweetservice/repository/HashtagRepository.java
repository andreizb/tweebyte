package ro.tweebyte.tweetservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagProjection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HashtagRepository extends JpaRepository<HashtagEntity, UUID> {

    Optional<HashtagEntity> findByText(String text);

    @Query(value = "SELECT h.id as id, h.text as text, COUNT(th.tweet_id) as count " +
        "FROM hashtags h " +
        "INNER JOIN tweet_hashtag th ON h.id = th.hashtag_id " +
        "GROUP BY h.id, h.text " +
        "ORDER BY count DESC",
        countQuery = "SELECT COUNT(DISTINCT h.id) " +
            "FROM hashtags h " +
            "INNER JOIN tweet_hashtag th ON h.id = th.hashtag_id",
        nativeQuery = true)
    List<HashtagProjection> findPopularHashtags(Pageable pageable);

}
