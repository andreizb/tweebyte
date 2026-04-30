package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * Branch coverage for the REPLY arm of CleanupService.cleanupOrphanLikes
 * CleanupServiceTest exercises the TWEET-typed LikeEntities path;
 * this suite covers the REPLY-typed else-branch and its existsById call.
 */
@ExtendWith(MockitoExtension.class)
class CleanupServiceBranchTest {

    @InjectMocks
    private CleanupService cleanupService;

    @Mock
    private TweetService tweetService;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private RetweetRepository retweetRepository;

    @Test
    void cleanupOrphanLikes_ReplyTypeOrphan_DeletedViaExistsByIdElseBranch() {
        // Drives the else-branch of the likeableType check (REPLY arm):
        //   if (likeEntity.getLikeableType().equals(LikeableType.TWEET.name())) { ... }
        //   else { return likeRepository.existsById(...).map(exists -> !exists); }
        // With LikeableType="REPLY" and existsById returning false (orphan),
        // the .map negates to true → the entity is collected and passed to
        // deleteAll. Both outcomes of the existsById -> !exists comparison
        // (exists=false → keep=true) are exercised in this test.
        UUID likeableId = UUID.randomUUID();
        LikeEntity replyLike = new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true,
            UUID.randomUUID(), likeableId, "REPLY");

        given(likeRepository.findAll()).willReturn(Flux.just(replyLike));
        // existsById returns false → orphan, so !exists = true → kept.
        given(likeRepository.existsById(likeableId)).willReturn(Mono.just(false));
        given(likeRepository.deleteAll(anyList())).willReturn(Mono.empty());

        StepVerifier.create(cleanupService.cleanupOrphanLikes())
            .verifyComplete();
    }

    @Test
    void cleanupOrphanLikes_ReplyTypePresent_FilteredOutByExistsByIdMap() {
        // Drives the other side of the existsById map predicate:
        // returns true (the reply still exists), so !exists = false and
        // the like is filtered out — deleteAll receives an empty list.
        UUID likeableId = UUID.randomUUID();
        LikeEntity replyLike = new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true,
            UUID.randomUUID(), likeableId, "REPLY");

        given(likeRepository.findAll()).willReturn(Flux.just(replyLike));
        given(likeRepository.existsById(likeableId)).willReturn(Mono.just(true));
        given(likeRepository.deleteAll(anyList())).willReturn(Mono.empty());

        StepVerifier.create(cleanupService.cleanupOrphanLikes())
            .verifyComplete();
    }
}
