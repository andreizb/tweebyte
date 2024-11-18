//package ro.tweebyte.interactionservice.repository;
//
//import org.checkerframework.checker.guieffect.qual.UI;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//import ro.tweebyte.interactionservice.entity.FollowEntity;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@DataJpaTest
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//@ExtendWith(SpringExtension.class)
//class FollowRepositoryTest {
//
//    @Autowired
//    private FollowRepository followRepository;
//
//    @Autowired
//    private TestEntityManager entityManager;
//
//    @Test
//    void testFindByStatus() {
//        UUID followerId = UUID.randomUUID();
//        UUID followedId = UUID.randomUUID();
//        UUID followedId2 = UUID.randomUUID();
//
//        FollowEntity follow1 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId)
//            .status(FollowEntity.Status.PENDING)
//            .build();
//        FollowEntity follow2 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId2)
//            .status(FollowEntity.Status.ACCEPTED)
//            .build();
//
//        follow1.setCreatedAt(LocalDateTime.now());
//        follow2.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(follow1);
//        entityManager.persist(follow2);
//        entityManager.flush();
//
//        Page<FollowEntity> page = followRepository.findByStatus(FollowEntity.Status.PENDING, PageRequest.of(0, 10));
//
//        assertNotNull(page);
//        assertEquals(1, page.getTotalElements());
//        assertEquals(FollowEntity.Status.PENDING, page.getContent().get(0).getStatus());
//    }
//
//    @Test
//    void testFindByFollowerIdAndStatus() {
//        UUID followerId1 = UUID.randomUUID();
//        UUID followerId2 = UUID.randomUUID();
//        UUID followedId = UUID.randomUUID();
//
//        FollowEntity follow1 = FollowEntity.builder()
//            .followerId(followerId1)
//            .followedId(followedId)
//            .status(FollowEntity.Status.ACCEPTED)
//            .build();
//        FollowEntity follow2 = FollowEntity.builder()
//            .followerId(followerId2)
//            .followedId(followedId)
//            .status(FollowEntity.Status.PENDING)
//            .build();
//
//        follow1.setCreatedAt(LocalDateTime.now());
//        follow2.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(follow1);
//        entityManager.persist(follow2);
//        entityManager.flush();
//
//        List<FollowEntity> follows = followRepository.findByFollowerIdAndStatus(followerId1, FollowEntity.Status.ACCEPTED);
//
//        assertNotNull(follows);
//        assertEquals(1, follows.size());
//        assertEquals(followerId1, follows.get(0).getFollowerId());
//        assertEquals(FollowEntity.Status.ACCEPTED, follows.get(0).getStatus());
//    }
//
//    @Test
//    void testFindByFollowedIdAndStatusOrderByCreatedAtDesc() {
//        UUID followerId = UUID.randomUUID();
//        UUID followedId = UUID.randomUUID();
//        UUID followedId2 = UUID.randomUUID();
//
//        FollowEntity follow1 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId)
//            .status(FollowEntity.Status.ACCEPTED)
//            .build();
//        FollowEntity follow2 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId2)
//            .status(FollowEntity.Status.PENDING)
//            .build();
//
//        follow1.setCreatedAt(LocalDateTime.now());
//        follow2.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(follow1);
//        entityManager.persist(follow2);
//        entityManager.flush();
//
//        Page<FollowEntity> page = followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(
//            followedId, FollowEntity.Status.ACCEPTED, PageRequest.of(0, 10));
//
//        assertNotNull(page);
//        assertEquals(1, page.getTotalElements());
//        assertEquals(followedId, page.getContent().get(0).getFollowedId());
//        assertEquals(FollowEntity.Status.ACCEPTED, page.getContent().get(0).getStatus());
//    }
//
//    @Test
//    void testCountByFollowedIdAndStatus() {
//        UUID followerId = UUID.randomUUID();
//        UUID followedId = UUID.randomUUID();
//        UUID followedId2 = UUID.randomUUID();
//
//        FollowEntity follow1 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId)
//            .status(FollowEntity.Status.ACCEPTED)
//            .build();
//        FollowEntity follow2 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId2)
//            .status(FollowEntity.Status.PENDING)
//            .build();
//
//        follow1.setCreatedAt(LocalDateTime.now());
//        follow2.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(follow1);
//        entityManager.persist(follow2);
//        entityManager.flush();
//
//        long count = followRepository.countByFollowedIdAndStatus(followedId, FollowEntity.Status.ACCEPTED);
//
//        assertEquals(1, count);
//    }
//
//    @Test
//    void testCountByFollowerIdAndStatus() {
//        UUID followerId = UUID.randomUUID();
//        UUID followedId = UUID.randomUUID();
//        UUID followedId2 = UUID.randomUUID();
//
//        FollowEntity follow1 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId)
//            .status(FollowEntity.Status.ACCEPTED)
//            .build();
//        FollowEntity follow2 = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId2)
//            .status(FollowEntity.Status.PENDING)
//            .build();
//
//        follow1.setCreatedAt(LocalDateTime.now());
//        follow2.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(follow1);
//        entityManager.persist(follow2);
//        entityManager.flush();
//
//        long count = followRepository.countByFollowerIdAndStatus(followerId, FollowEntity.Status.ACCEPTED);
//
//        assertEquals(1, count);
//    }
//
//    @Test
//    void testDeleteByFollowerIdAndFollowedId() {
//        UUID followerId = UUID.randomUUID();
//        UUID followedId = UUID.randomUUID();
//
//        FollowEntity follow = FollowEntity.builder()
//            .followerId(followerId)
//            .followedId(followedId)
//            .status(FollowEntity.Status.ACCEPTED)
//            .build();
//
//        follow.setCreatedAt(LocalDateTime.now());
//
//        entityManager.persist(follow);
//        entityManager.flush();
//
//        followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);
//
//        Optional<FollowEntity> deletedFollow = followRepository.findById(follow.getId());
//        assertFalse(deletedFollow.isPresent());
//    }
//
//}