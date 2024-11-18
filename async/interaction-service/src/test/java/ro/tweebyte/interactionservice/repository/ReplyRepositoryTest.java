//package ro.tweebyte.interactionservice.repository;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//import ro.tweebyte.interactionservice.entity.ReplyEntity;
//
//import java.time.LocalDateTime;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@ExtendWith(SpringExtension.class)
//@DataJpaTest
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//class ReplyRepositoryTest {
//
//    @Autowired
//    private ReplyRepository replyRepository;
//
//    @Autowired
//    private TestEntityManager entityManager;
//
//    @Test
//    void testFindByIdAndUserId() {
//        UUID userId = UUID.randomUUID();
//        ReplyEntity reply = ReplyEntity.builder()
//            .userId(userId)
//            .content("Sample reply content")
//            .build();
//        reply.setCreatedAt(LocalDateTime.now());
//
//        UUID id = entityManager.persist(reply).getId();
//        entityManager.flush();
//
//        Optional<ReplyEntity> foundReply = replyRepository.findByIdAndUserId(id, userId);
//
//        assertTrue(foundReply.isPresent());
//        assertEquals(reply.getId(), foundReply.get().getId());
//        assertEquals(reply.getUserId(), foundReply.get().getUserId());
//    }
//
//    @Test
//    void testFindByTweetIdOrderByCreatedAtDesc() {
//        UUID tweetId = UUID.randomUUID();
//        ReplyEntity reply1 = ReplyEntity.builder()
//            .tweetId(tweetId)
//            .content("Reply 1")
//            .build();
//        ReplyEntity reply2 = ReplyEntity.builder()
//            .tweetId(tweetId)
//            .content("Reply 2")
//            .build();
//        ReplyEntity reply3 = ReplyEntity.builder()
//            .tweetId(tweetId)
//            .content("Reply 3")
//            .build();
//
//        reply1.setCreatedAt(LocalDateTime.now());
//        reply2.setCreatedAt(LocalDateTime.now());
//        reply3.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(reply1);
//        entityManager.persist(reply2);
//        entityManager.persist(reply3);
//        entityManager.flush();
//
//        Page<ReplyEntity> foundReplies = replyRepository.findByTweetIdOrderByCreatedAtDesc(tweetId, PageRequest.of(0, 10));
//
//        assertEquals(3, foundReplies.getTotalElements());
//        assertEquals(reply3.getId(), foundReplies.getContent().get(0).getId());
//        assertEquals(reply2.getId(), foundReplies.getContent().get(1).getId());
//        assertEquals(reply1.getId(), foundReplies.getContent().get(2).getId());
//    }
//
//    @Test
//    void testCountByTweetId() {
//        UUID tweetId = UUID.randomUUID();
//        ReplyEntity reply1 = ReplyEntity.builder()
//            .tweetId(tweetId)
//            .content("Reply 1")
//            .build();
//        ReplyEntity reply2 = ReplyEntity.builder()
//            .tweetId(tweetId)
//            .content("Reply 2")
//            .build();
//        ReplyEntity reply3 = ReplyEntity.builder()
//            .tweetId(tweetId)
//            .content("Reply 3")
//            .build();
//
//        reply1.setCreatedAt(LocalDateTime.now());
//        reply2.setCreatedAt(LocalDateTime.now());
//        reply3.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(reply1);
//        entityManager.persist(reply2);
//        entityManager.persist(reply3);
//        entityManager.flush();
//
//        long count = replyRepository.countByTweetId(tweetId);
//
//        assertEquals(3, count);
//    }
//
//}