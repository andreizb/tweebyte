package ro.tweebyte.tweetservice.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

import java.util.UUID;

@Repository
public interface HashtagRepository extends ReactiveCrudRepository<HashtagEntity, UUID> {

    Mono<HashtagEntity> findByText(String text);

    @Query("SELECT h.id as id, h.text as text, COUNT(th.tweet_id) as count " +
        "FROM hashtags h " +
        "JOIN tweet_hashtag th ON h.id = th.hashtag_id " +
        "GROUP BY h.id, h.text " +
        "ORDER BY COUNT(th.tweet_id) DESC")
    Flux<HashtagDto> findPopularHashtags();

    @Query("SELECT h.* FROM hashtags h " +
        "JOIN tweet_hashtag th ON h.id = th.hashtag_id " +
        "WHERE th.tweet_id = :tweetId")
    Flux<HashtagEntity> findHashtagsByTweetId(UUID tweetId);

}
