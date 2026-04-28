package ro.tweebyte.interactionservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.LikeEntity;

import java.util.UUID;

@Repository
public interface LikeRepository extends ReactiveCrudRepository<LikeEntity, UUID> {

    Flux<LikeEntity> findByUserIdAndLikeableType(UUID userId, String likeableType);

    Flux<LikeEntity> findByLikeableIdAndLikeableType(UUID likeableId, String likeableType);

    Mono<Long> countByLikeableIdAndLikeableType(UUID likeableId, String likeableType);

    Mono<Void> deleteByUserIdAndLikeableIdAndLikeableType(UUID userId, UUID likeableId, String likeableType);

}
