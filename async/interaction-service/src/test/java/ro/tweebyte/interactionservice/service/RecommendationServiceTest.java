package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@SpringBootTest
class RecommendationServiceTest {

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

    @Test
    void testGetUserRecommendations() {
        UUID userId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);

        ReflectionTestUtils.setField(recommendationService, "self", recommendationService);

        Page<FollowEntity> page = new PageImpl<>(Collections.singletonList(new FollowEntity()));
        when(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(any(), any(), any())).thenReturn(page);

        when(userService.getUserSummary(any())).thenReturn(new UserDto());

        when(cacheManager.getCache(any())).thenReturn(mock(Cache.class));

        recommendationService.getUserRecommendations(userId, pageable);

        verify(followRepository, times(2)).findByFollowerIdAndStatusOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void testComputePopularUsers() {
        when(followRepository.findAllFollowedIds()).thenReturn(Collections.emptyList());
        when(tweetService.getUserTweetsSummary(any())).thenReturn(Collections.emptyList());
        when(likeService.getTweetLikesCount(any())).thenReturn(CompletableFuture.completedFuture(0L));
        when(retweetService.getRetweetCountOfTweet(any())).thenReturn(CompletableFuture.completedFuture(0L));
        when(followRepository.countByFollowedIdAndStatus(any(), any())).thenReturn(0L);

        recommendationService.computePopularUsers();

        verify(followRepository).findAllFollowedIds();
        verify(tweetService, times(0)).getUserTweetsSummary(any());
        verify(likeService, times(0)).getTweetLikesCount(any());
    }

    @Test
    void testComputePopularHashtags() throws ExecutionException, InterruptedException {
        when(tweetService.getPopularHashtags()).thenReturn(Collections.emptyList());

        recommendationService.computePopularHashtags().get();

        verify(tweetService).getPopularHashtags();
    }

    @Test
    void testFindTweetSummaries() throws ExecutionException, InterruptedException {
        List<UUID> tweetIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(likeRepository.countByLikeableIdAndLikeableType(any(), eq(LikeEntity.LikeableType.TWEET)))
            .thenReturn(5L);
        when(retweetRepository.countByOriginalTweetId(any()))
            .thenReturn(3L);
        when(replyRepository.countByTweetId(any()))
            .thenReturn(2L);
        when(replyRepository.findTopReplyByLikesForTweetId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(new ReplyDto())));

        List<TweetSummaryDto> result = recommendationService.findTweetSummaries(tweetIds).get();

        assertNotNull(result);
        assertEquals(tweetIds.size(), result.size());

        verify(likeRepository, times(tweetIds.size()))
            .countByLikeableIdAndLikeableType(any(), any());
        verify(retweetRepository, times(tweetIds.size()))
            .countByOriginalTweetId(any());
        verify(replyRepository, times(tweetIds.size()))
            .countByTweetId(any());
    }

    @Test
    void testFetchPopularUsersWithCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(cache);
        when(cache.get("p0", Map.class)).thenReturn(Map.of(UUID.randomUUID(), 1.0));

        PageRequest pageable = PageRequest.of(0, 10);
        Collection<UUID> result = ReflectionTestUtils.invokeMethod(
            recommendationService, "fetchPopularUsers", pageable
        );

        assertNotNull(result);
        verify(cacheManager).getCache("popular_users");
    }

    @Test
    void testFetchPopularUsersWithoutCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(cache);
        when(cache.get("p0", Map.class)).thenReturn(null);

        Map<UUID, Double> popularUsers = Map.of(UUID.randomUUID(), 1.0);

        RecommendationService spyRecommendationService = spy(recommendationService);
        ReflectionTestUtils.setField(spyRecommendationService, "self", spyRecommendationService);
        doReturn(popularUsers).when(spyRecommendationService).computePopularUsers();

        PageRequest pageable = PageRequest.of(0, 10);
        Collection<UUID> result = ReflectionTestUtils.invokeMethod(
            spyRecommendationService, "fetchPopularUsers", pageable
        );

        assertNotNull(result);
        assertEquals(popularUsers.keySet(), result);
        verify(spyRecommendationService).computePopularUsers();
    }

    @Test
    void testComputePopularUsersAndScore() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID tweetId1 = UUID.randomUUID();
        UUID tweetId2 = UUID.randomUUID();

        when(followRepository.findAllFollowedIds()).thenReturn(List.of(userId1, userId2));
        when(tweetService.getUserTweetsSummary(userId1)).thenReturn(List.of(new TweetDto(tweetId1, null, null, null, null, null, null, null, null, null)));
        when(tweetService.getUserTweetsSummary(userId2)).thenReturn(List.of(new TweetDto(tweetId2, null, null, null, null, null, null, null, null, null)));
        when(followRepository.countByFollowedIdAndStatus(eq(userId1), eq(FollowEntity.Status.ACCEPTED))).thenReturn(10L);
        when(followRepository.countByFollowedIdAndStatus(eq(userId2), eq(FollowEntity.Status.ACCEPTED))).thenReturn(20L);
        when(likeService.getTweetLikesCount(eq(tweetId1))).thenReturn(CompletableFuture.completedFuture(5L));
        when(likeService.getTweetLikesCount(eq(tweetId2))).thenReturn(CompletableFuture.completedFuture(15L));
        when(retweetService.getRetweetCountOfTweet(eq(tweetId1))).thenReturn(CompletableFuture.completedFuture(2L));
        when(retweetService.getRetweetCountOfTweet(eq(tweetId2))).thenReturn(CompletableFuture.completedFuture(4L));

        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("popular_users")).thenReturn(cache);

        Map<UUID, Double> result = recommendationService.computePopularUsers();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get(userId1) > 0);
        assertTrue(result.get(userId2) > 0);

        verify(followRepository).findAllFollowedIds();
        verify(tweetService, times(2)).getUserTweetsSummary(any());
        verify(followRepository, times(2)).countByFollowedIdAndStatus(any(), eq(FollowEntity.Status.ACCEPTED));
        verify(likeService, times(4)).getTweetLikesCount(any());
        verify(retweetService, times(4)).getRetweetCountOfTweet(any());
    }

}
