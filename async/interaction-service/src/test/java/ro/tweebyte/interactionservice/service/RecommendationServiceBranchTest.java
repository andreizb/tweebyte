package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests for RecommendationService — drives the compound predicate
 * inside getUserRecommendations through every arm:
 *  - candidate already in followedIds (left side false)
 *  - candidate equals the requesting userId (right side false)
 *  - candidate is genuinely new (both true)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceBranchTest {

    @Mock
    private UserService userService;
    @Mock
    private TweetService tweetService;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private RetweetRepository retweetRepository;
    @Mock
    private LikeService likeService;
    @Mock
    private RetweetService retweetService;
    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recommendationService, "self", recommendationService);
    }

    @Test
    void getUserRecommendations_candidateAlreadyFollowed_skipped() {
        // Branch: !followedIds.contains(...) is FALSE → short-circuits.
        UUID userId = UUID.randomUUID();
        UUID followedA = UUID.randomUUID();
        UUID followedB = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);

        FollowEntity firstHop = new FollowEntity();
        firstHop.setFollowedId(followedA);
        FollowEntity secondHop1 = new FollowEntity();
        secondHop1.setFollowedId(followedB); // already in followedIds (returned by 1st hop too)
        FollowEntity secondHop2 = new FollowEntity();
        secondHop2.setFollowedId(followedA); // also already in followedIds

        // First hop returns BOTH followedA and followedB so any candidate from second hop is in set.
        FollowEntity firstHopExtra = new FollowEntity();
        firstHopExtra.setFollowedId(followedB);
        Page<FollowEntity> firstHopPage = new PageImpl<>(List.of(firstHop, firstHopExtra));
        Page<FollowEntity> secondHopPage = new PageImpl<>(List.of(secondHop1, secondHop2));

        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(userId), any(), any()))
            .thenReturn(firstHopPage);
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(followedA), any(), any()))
            .thenReturn(secondHopPage);
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(followedB), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        Cache popularCache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(popularCache);
        when(popularCache.get(eq("p0"), eq(Map.class))).thenReturn(Map.of());

        when(userService.getUserSummary(any())).thenReturn(new UserDto());

        List<UserDto> result = recommendationService.getUserRecommendations(userId, pageable);

        assertNotNull(result);
        // Every second-hop entity was already in followedIds, so userService never called.
        verify(userService, never()).getUserSummary(any());
    }

    @Test
    void getUserRecommendations_candidateIsRequestingUser_skipped() {
        // Branch: !userId.equals(...) is FALSE → short-circuits at right side.
        UUID userId = UUID.randomUUID();
        UUID followedA = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);

        FollowEntity firstHop = new FollowEntity();
        firstHop.setFollowedId(followedA);

        FollowEntity secondHop = new FollowEntity();
        secondHop.setFollowedId(userId); // candidate == userId itself

        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(userId), any(), any()))
            .thenReturn(new PageImpl<>(List.of(firstHop)));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(followedA), any(), any()))
            .thenReturn(new PageImpl<>(List.of(secondHop)));

        Cache popularCache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(popularCache);
        when(popularCache.get(eq("p0"), eq(Map.class))).thenReturn(Map.of());

        List<UserDto> result = recommendationService.getUserRecommendations(userId, pageable);

        assertNotNull(result);
        // Self-recommendation should be filtered out.
        verify(userService, never()).getUserSummary(eq(userId));
    }

    @Test
    void getUserRecommendations_popularUserAlreadyFollowed_skipped() {
        // Branch: !followedIds.contains(popularId) — both arms via two popular ids,
        // one already-followed and one new.
        UUID userId = UUID.randomUUID();
        UUID followedA = UUID.randomUUID();
        UUID popularNew = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);

        FollowEntity firstHop = new FollowEntity();
        firstHop.setFollowedId(followedA);

        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(userId), any(), any()))
            .thenReturn(new PageImpl<>(List.of(firstHop)));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(followedA), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        Cache popularCache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(popularCache);
        // followedA already followed (skipped), popularNew new (added)
        when(popularCache.get(eq("p0"), eq(Map.class)))
            .thenReturn(Map.of(followedA, 5.0, popularNew, 3.0));

        when(userService.getUserSummary(eq(popularNew))).thenReturn(new UserDto());

        List<UserDto> result = recommendationService.getUserRecommendations(userId, pageable);

        assertNotNull(result);
        verify(userService).getUserSummary(eq(popularNew));
        verify(userService, never()).getUserSummary(eq(followedA));
    }

    @Test
    void getUserRecommendations_genuinelyNewCandidate_isAdded() {
        // Branch: both sides TRUE → recommendation accumulated.
        UUID userId = UUID.randomUUID();
        UUID followedA = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);

        FollowEntity firstHop = new FollowEntity();
        firstHop.setFollowedId(followedA);

        FollowEntity secondHop = new FollowEntity();
        secondHop.setFollowedId(candidate); // not in followedIds, not userId

        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(userId), any(), any()))
            .thenReturn(new PageImpl<>(List.of(firstHop)));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(eq(followedA), any(), any()))
            .thenReturn(new PageImpl<>(List.of(secondHop)));

        Cache popularCache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(popularCache);
        when(popularCache.get(eq("p0"), eq(Map.class))).thenReturn(Map.of());

        when(userService.getUserSummary(eq(candidate))).thenReturn(new UserDto());

        List<UserDto> result = recommendationService.getUserRecommendations(userId, pageable);

        assertNotNull(result);
        verify(userService).getUserSummary(eq(candidate));
    }
}
