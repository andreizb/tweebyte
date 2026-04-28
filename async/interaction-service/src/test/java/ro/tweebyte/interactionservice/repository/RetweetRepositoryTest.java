//package ro.tweebyte.interactionservice.repository;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//import ro.tweebyte.interactionservice.entity.RetweetEntity;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@ExtendWith(SpringExtension.class)
//@DataJpaTest
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//class RetweetRepositoryTest {
//
//    @Autowired
//    private RetweetRepository retweetRepository;
//
//    @Autowired
//    private TestEntityManager entityManager;
//
//    @Test
//    void testFindByRetweeterId() {
//        UUID userId = UUID.randomUUID();
//        RetweetEntity retweet = RetweetEntity.builder()
//            .retweeterId(userId)
//            .content("Sample retweet content")
//            .build();
//        retweet.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(retweet);
//        entityManager.flush();
//
//        Page<RetweetEntity> foundRetweets = retweetRepository.findByRetweeterId(userId, Pageable.unpaged());
//
//        assertTrue(foundRetweets.getContent().stream().anyMatch(rt -> rt.getId().equals(retweet.getId())));
//    }
//
//    @Test
//    void testFindByOriginalTweetId() {
//        UUID tweetId = UUID.randomUUID();
//        RetweetEntity retweet = RetweetEntity.builder()
//            .originalTweetId(tweetId)
//            .content("Sample retweet content")
//            .build();
//        retweet.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(retweet);
//        entityManager.flush();
//
//        Page<RetweetEntity> foundRetweets = retweetRepository.findByOriginalTweetId(tweetId, Pageable.unpaged());
//
//        assertTrue(foundRetweets.getContent().stream().anyMatch(rt -> rt.getId().equals(retweet.getId())));
//    }
//
//    @Test
//    void testCountByOriginalTweetId() {
//        UUID tweetId = UUID.randomUUID();
//        RetweetEntity retweet = RetweetEntity.builder()
//            .originalTweetId(tweetId)
//            .content("Sample retweet content")
//            .build();
//        retweet.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(retweet);
//        entityManager.flush();
//
//        long count = retweetRepository.countByOriginalTweetId(tweetId);
//
//        assertEquals(1, count);
//    }
//
//}