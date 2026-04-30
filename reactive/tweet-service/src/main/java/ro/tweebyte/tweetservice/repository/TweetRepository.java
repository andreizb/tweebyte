package ro.tweebyte.tweetservice.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TweetRepository extends ReactiveCrudRepository<TweetEntity, UUID> {

    Mono<TweetEntity> findByIdAndUserId(UUID id, UUID userId);

    Flux<TweetEntity> findByUserId(UUID userId);

    Flux<TweetEntity> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds);

    @Query("SELECT * FROM tweets WHERE content ILIKE '%' || :searchTerm || '%'")
    Flux<TweetEntity> findBySimilarity(String searchTerm);

    // The join target is `tweet_hashtag` (singular) as defined in schema.sql,
    // matching the async stack's repository — a divergent table name here
    // would surface as `bad SQL grammar` 500s on every hashtag search.
    @Query("SELECT t.* FROM tweets t JOIN tweet_hashtag th ON t.id = th.tweet_id JOIN hashtags h ON th.hashtag_id = h.id WHERE h.text = :searchTerm")
    Flux<TweetEntity> findByHashtag(String searchTerm);

}
