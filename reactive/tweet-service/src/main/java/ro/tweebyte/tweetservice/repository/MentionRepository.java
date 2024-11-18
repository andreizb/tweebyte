package ro.tweebyte.tweetservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.entity.MentionEntity;

import java.util.UUID;

@Repository
public interface MentionRepository extends ReactiveCrudRepository<MentionEntity, UUID> {

    Flux<MentionEntity> findMentionsByTweetId(UUID tweetId);

    Mono<Void> deleteByTweetId(UUID tweetId);

}
