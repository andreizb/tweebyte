package ro.tweebyte.userservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ro.tweebyte.userservice.entity.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmail(String email);

    boolean existsByUserName(String userName);

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUserName(String userName);

    @Query(
            value = "SELECT * FROM users WHERE user_name ILIKE :query",
            nativeQuery = true
    )
    List<UserEntity> searchUsers(@Param("query") String query);

}
