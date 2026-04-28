package ro.tweebyte.tweetservice.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.HashtagProjection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
public class HashtagRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HashtagRepository hashtagRepository;

    @Test
    void findByText() {
        String text = "example";
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setText(text);

        entityManager.persist(hashtagEntity);
        entityManager.flush();

        Optional<HashtagEntity> foundHashtag = hashtagRepository.findByText(text);

        assertTrue(foundHashtag.isPresent());
        assertEquals(text, foundHashtag.get().getText());
    }

    @Test
    void findPopularHashtags() {
        HashtagEntity hashtag1 = new HashtagEntity();
        hashtag1.setText("hashtag1");
        entityManager.persist(hashtag1);

        HashtagEntity hashtag2 = new HashtagEntity();
        hashtag2.setText("hashtag2");
        entityManager.persist(hashtag2);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setHashtags(Set.of(hashtag1, hashtag2));
        tweetEntity.setContent("asdf");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        tweetEntity.setUserId(UUID.randomUUID());
        entityManager.persist(tweetEntity);

        Pageable pageable = PageRequest.of(0, 10);
        List<HashtagProjection> popularHashtags = hashtagRepository.findPopularHashtags(pageable);

        assertEquals(2, popularHashtags.size());
    }

}