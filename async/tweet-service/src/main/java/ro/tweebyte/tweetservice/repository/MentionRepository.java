package ro.tweebyte.tweetservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.tweebyte.tweetservice.entity.MentionEntity;

import java.util.UUID;

@Repository
public interface MentionRepository extends JpaRepository<MentionEntity, UUID> {
}
