package ro.tweebyte.interactionservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.RetweetEntity;

import java.util.UUID;

@Repository
public interface RetweetRepository extends ReactiveCrudRepository<RetweetEntity, UUID> {

    Flux<RetweetEntity> findByRetweeterId(UUID userId);

    Flux<RetweetEntity> findByOriginalTweetId(UUID tweetId);

    Mono<Long> countByOriginalTweetId(UUID tweetId);

}
