//package ro.tweebyte.interactionservice.repository;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.data.domain.Pageable;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//import ro.tweebyte.interactionservice.entity.LikeEntity;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@ExtendWith(SpringExtension.class)
//@DataJpaTest
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//class LikeRepositoryTest {
//
//    @Autowired
//    private LikeRepository likeRepository;
//
//    @Autowired
//    private TestEntityManager entityManager;
//
//    @Test
//    void testFindByUserIdAndLikeableType() {
//        UUID userId = UUID.randomUUID();
//        UUID likeableId = UUID.randomUUID();
//        LikeEntity like = LikeEntity.builder()
//            .userId(userId)
//            .likeableId(likeableId)
//            .likeableType(LikeEntity.LikeableType.TWEET)
//            .build();
//        like.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(like);
//        entityManager.flush();
//
//        List<LikeEntity> foundLikes = likeRepository.findByUserIdAndLikeableType(userId, LikeEntity.LikeableType.TWEET, Pageable.unpaged()).getContent();
//
//        assertEquals(1, foundLikes.size());
//        assertEquals(like.getUserId(), foundLikes.get(0).getUserId());
//        assertEquals(like.getLikeableType(), foundLikes.get(0).getLikeableType());
//    }
//
//    @Test
//    void testFindByLikeableIdAndLikeableType() {
//        UUID userId = UUID.randomUUID();
//        UUID likeableId = UUID.randomUUID();
//        LikeEntity like = LikeEntity.builder()
//            .userId(userId)
//            .likeableId(likeableId)
//            .likeableType(LikeEntity.LikeableType.TWEET)
//            .build();
//        like.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(like);
//        entityManager.flush();
//
//        List<LikeEntity> foundLikes = likeRepository.findByLikeableIdAndLikeableType(likeableId, LikeEntity.LikeableType.TWEET, Pageable.unpaged()).getContent();
//
//        assertEquals(1, foundLikes.size());
//        assertEquals(like.getLikeableId(), foundLikes.get(0).getLikeableId());
//        assertEquals(like.getLikeableType(), foundLikes.get(0).getLikeableType());
//    }
//
//    @Test
//    void testCountByLikeableIdAndLikeableType() {
//        UUID userId = UUID.randomUUID();
//        UUID likeableId = UUID.randomUUID();
//        LikeEntity like = LikeEntity.builder()
//            .userId(userId)
//            .likeableId(likeableId)
//            .likeableType(LikeEntity.LikeableType.TWEET)
//            .build();
//
//        like.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(like);
//        entityManager.flush();
//
//        long count = likeRepository.countByLikeableIdAndLikeableType(likeableId, LikeEntity.LikeableType.TWEET);
//
//        assertEquals(1, count);
//    }
//
//    @Test
//    void testDeleteByUserIdAndLikeableIdAndLikeableType() {
//        UUID userId = UUID.randomUUID();
//        UUID likeableId = UUID.randomUUID();
//        LikeEntity like = LikeEntity.builder()
//            .userId(userId)
//            .likeableId(likeableId)
//            .likeableType(LikeEntity.LikeableType.TWEET)
//            .build();
//        like.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(like);
//        entityManager.flush();
//
//        likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, likeableId, LikeEntity.LikeableType.TWEET);
//
//        assertFalse(likeRepository.findById(like.getId()).isPresent());
//    }
//
//}