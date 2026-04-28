package ro.tweebyte.tweetservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.entity.TweetHashtagEntity;

import java.util.UUID;

public interface TweetHashtagRepository extends ReactiveCrudRepository<TweetHashtagEntity, UUID> {

    Mono<Void> deleteByTweetId(UUID tweetId);

    Flux<TweetHashtagEntity> findByTweetId(UUID tweetId);

    Mono<Void> deleteByTweetIdAndHashtagId(UUID tweetId, UUID hashtagId);

}
