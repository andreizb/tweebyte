package ro.tweebyte.userservice.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.entity.UserEntity;

import java.util.UUID;

@Repository
public interface UserRepository extends ReactiveCrudRepository<UserEntity, UUID> {

    Mono<UserEntity> findByEmail(String email);

    Mono<UserEntity> findByUserName(String userName);

    @Query("SELECT * FROM users WHERE user_name LIKE :query")
    Flux<UserEntity> searchUsers(String query);

    @Query("SELECT * FROM users WHERE user_name LIKE :searchTerm ORDER BY user_name") // Simplified query
    Flux<UserEntity> findBySimilarity(String searchTerm);

}
