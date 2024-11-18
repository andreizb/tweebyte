package ro.tweebyte.tweetservice.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
public class TweetRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TweetRepository tweetRepository;

    @Test
    void findById() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setContent("asdf");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        tweetEntity.setUserId(UUID.randomUUID());
        UUID tweetId = entityManager.persist(tweetEntity).getId();
        entityManager.flush();

        Optional<TweetEntity> foundTweet = tweetRepository.findById(tweetId);

        assertTrue(foundTweet.isPresent());
        assertEquals(tweetId, foundTweet.get().getId());
    }

    @Test
    void findByIdAndUserId() {
        UUID userId = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setUserId(userId);
        tweetEntity.setContent("asdf");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        UUID tweetId = entityManager.persist(tweetEntity).getId();
        entityManager.flush();

        Optional<TweetEntity> foundTweet = tweetRepository.findByIdAndUserId(tweetId, userId);

        assertTrue(foundTweet.isPresent());
        assertEquals(tweetId, foundTweet.get().getId());
        assertEquals(userId, foundTweet.get().getUserId());
    }

    @Test
    void findByUserId() {
        UUID userId = UUID.randomUUID();
        TweetEntity tweet1 = new TweetEntity();
        tweet1.setId(UUID.randomUUID());
        tweet1.setUserId(userId);
        tweet1.setContent("asdf");
        tweet1.setCreatedAt(LocalDateTime.now());
        entityManager.persist(tweet1);

        TweetEntity tweet2 = new TweetEntity();
        tweet2.setId(UUID.randomUUID());
        tweet2.setUserId(userId);
        tweet2.setContent("asdf");
        tweet2.setCreatedAt(LocalDateTime.now());
        entityManager.persist(tweet2);

        entityManager.flush();

        List<TweetEntity> foundTweets = tweetRepository.findByUserId(userId);

        assertEquals(2, foundTweets.size());
    }

    @Test
    void findByHashtag() {
        String hashtag = "exampleHashtag";

        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setText(hashtag);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setUserId(UUID.randomUUID());
        tweetEntity.setContent("asdf");
        tweetEntity.setCreatedAt(LocalDateTime.now());
        tweetEntity.setHashtags(Set.of(hashtagEntity));
        UUID tweetId = entityManager.persist(tweetEntity).getId();
        entityManager.flush();

        List<TweetEntity> foundTweets = tweetRepository.findByHashtag(hashtag);

        assertEquals(1, foundTweets.size());
        assertEquals(tweetId, foundTweets.get(0).getId());
    }

    @Test
    void findByUserIdInOrderByCreatedAtDesc() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        TweetEntity tweet1 = new TweetEntity();
        tweet1.setId(UUID.randomUUID());
        tweet1.setUserId(userId1);
        tweet1.setContent("asdf");
        tweet1.setCreatedAt(LocalDateTime.now());
        entityManager.persist(tweet1);

        TweetEntity tweet2 = new TweetEntity();
        tweet2.setId(UUID.randomUUID());
        tweet2.setUserId(userId2);
        tweet2.setContent("asdf");
        tweet2.setCreatedAt(LocalDateTime.now());
        entityManager.persist(tweet2);

        entityManager.flush();

        List<UUID> userIds = List.of(userId1, userId2);
        List<TweetEntity> foundTweets = tweetRepository.findByUserIdInOrderByCreatedAtDesc(userIds);

        assertEquals(2, foundTweets.size());
        assertTrue(foundTweets.get(0).getCreatedAt().isAfter(foundTweets.get(1).getCreatedAt()));
    }
}