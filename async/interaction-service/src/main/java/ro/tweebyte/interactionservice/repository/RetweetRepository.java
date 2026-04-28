package ro.tweebyte.interactionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.tweebyte.interactionservice.entity.RetweetEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RetweetRepository extends JpaRepository<RetweetEntity, UUID> {

    List<RetweetEntity> findByRetweeterId(UUID userId);

    List<RetweetEntity> findByOriginalTweetId(UUID tweetId);

    long countByOriginalTweetId(UUID tweetId);

}
