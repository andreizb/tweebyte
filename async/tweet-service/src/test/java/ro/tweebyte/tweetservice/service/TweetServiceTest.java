package ro.tweebyte.tweetservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
public class TweetServiceTest {

    @Mock
    private InteractionClient interactionClient;

    @Mock
    private UserClient userClient;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private TweetMapper tweetMapper;

    @InjectMocks
    private UserService userService;

    @Mock
    private HashtagService hashtagService;

    @Mock
    private MentionService mentionService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TweetService tweetService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(tweetService, "executorService", executorService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGetUserFeed() {
        UUID userId = UUID.randomUUID();
        List<UUID> followedIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(interactionClient.getFollowedIds(eq(userId))).thenReturn(CompletableFuture.completedFuture(followedIds));

        TweetEntity tweetEntity1 = new TweetEntity();
        tweetEntity1.setId(UUID.randomUUID());

        TweetEntity tweetEntity2 = new TweetEntity();
        tweetEntity2.setId(UUID.randomUUID());

        List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
        when(tweetRepository.findByUserIdInOrderByCreatedAtDesc(followedIds)).thenReturn(tweetEntities);

        ReplyDto replyDto = new ReplyDto();
        replyDto.setId(UUID.randomUUID());

        TweetSummaryDto tweetSummary1 = new TweetSummaryDto();
        tweetSummary1.setTweetId(tweetEntity1.getId());
        tweetSummary1.setLikesCount(10L);
        tweetSummary1.setRepliesCount(5L);
        tweetSummary1.setRetweetsCount(3L);
        tweetSummary1.setTopReply(replyDto);

        TweetSummaryDto tweetSummary2 = new TweetSummaryDto();
        tweetSummary2.setTweetId(tweetEntity2.getId());
        tweetSummary2.setLikesCount(15L);
        tweetSummary2.setRepliesCount(8L);
        tweetSummary2.setRetweetsCount(4L);
        tweetSummary2.setTopReply(replyDto);

        List<TweetSummaryDto> tweetSummaryDtos = Arrays.asList(tweetSummary1, tweetSummary2);
        when(interactionClient.getTweetSummaries(anyList(), eq("AUTH_TOKEN")))
                .thenReturn(CompletableFuture.completedFuture(tweetSummaryDtos));

        when(tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class)))
                .thenReturn(new TweetDto());

        CompletableFuture<List<TweetDto>> result = tweetService.getUserFeed(userId, "AUTH_TOKEN");

        assertEquals(2, result.join().size());
        verify(interactionClient).getFollowedIds(eq(userId));
        verify(tweetRepository).findByUserIdInOrderByCreatedAtDesc(followedIds);
        verify(interactionClient).getTweetSummaries(anyList(), eq("AUTH_TOKEN"));
        verify(tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class));
    }

    @Test
    void testGetUserFeedFallbackToCache() throws Exception {
        UUID userId = UUID.randomUUID();
        List<UUID> followedIdsFromCache = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());

        when(interactionClient.getFollowedIds(eq(userId)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Service error")));

        String redisCacheKey = "followed_cache::" + userId;
        when(valueOperations.get(redisCacheKey))
                .thenReturn("[\"" + followedIdsFromCache.get(0) + "\", \"" + followedIdsFromCache.get(1) + "\"]");
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(followedIdsFromCache);

        TweetEntity tweetEntity1 = new TweetEntity();
        tweetEntity1.setId(UUID.randomUUID());

        TweetEntity tweetEntity2 = new TweetEntity();
        tweetEntity2.setId(UUID.randomUUID());

        List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
        when(tweetRepository.findByUserIdInOrderByCreatedAtDesc(followedIdsFromCache)).thenReturn(tweetEntities);

        ReplyDto replyDto = new ReplyDto();

        TweetSummaryDto tweetSummary1 = new TweetSummaryDto();
        tweetSummary1.setTweetId(tweetEntity1.getId());
        tweetSummary1.setLikesCount(10L);
        tweetSummary1.setRepliesCount(5L);
        tweetSummary1.setRetweetsCount(3L);
        tweetSummary1.setTopReply(replyDto);

        TweetSummaryDto tweetSummary2 = new TweetSummaryDto();
        tweetSummary2.setTweetId(tweetEntity2.getId());
        tweetSummary2.setLikesCount(15L);
        tweetSummary2.setRepliesCount(8L);
        tweetSummary2.setRetweetsCount(4L);
        tweetSummary2.setTopReply(replyDto);

        List<TweetSummaryDto> tweetSummaryDtos = Arrays.asList(tweetSummary1, tweetSummary2);
        when(interactionClient.getTweetSummaries(anyList(), eq("AUTH_TOKEN")))
                .thenReturn(CompletableFuture.completedFuture(tweetSummaryDtos));
        when(tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class)))
                .thenReturn(new TweetDto());

        CompletableFuture<List<TweetDto>> result = tweetService.getUserFeed(userId, "AUTH_TOKEN");

        assertEquals(2, result.join().size());
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(redisCacheKey);
        verify(objectMapper).readValue(anyString(), any(TypeReference.class));
        verify(tweetRepository).findByUserIdInOrderByCreatedAtDesc(followedIdsFromCache);
        verify(tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class));
    }

    @Test
    void testGetUserTweets() {
        UUID userId = UUID.randomUUID();

        TweetEntity tweetEntity1 = new TweetEntity();
        tweetEntity1.setId(UUID.randomUUID());

        TweetEntity tweetEntity2 = new TweetEntity();
        tweetEntity2.setId(UUID.randomUUID());

        List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
        when(tweetRepository.findByUserId(userId)).thenReturn(tweetEntities);

        ReplyDto replyDto = new ReplyDto();
        replyDto.setId(UUID.randomUUID());

        TweetSummaryDto tweetSummary1 = new TweetSummaryDto();
        tweetSummary1.setTweetId(tweetEntity1.getId());
        tweetSummary1.setLikesCount(10L);
        tweetSummary1.setRepliesCount(5L);
        tweetSummary1.setRetweetsCount(3L);
        tweetSummary1.setTopReply(replyDto);

        TweetSummaryDto tweetSummary2 = new TweetSummaryDto();
        tweetSummary2.setTweetId(tweetEntity2.getId());
        tweetSummary2.setLikesCount(15L);
        tweetSummary2.setRepliesCount(8L);
        tweetSummary2.setRetweetsCount(4L);
        tweetSummary2.setTopReply(replyDto);

        List<TweetSummaryDto> tweetSummaryDtos = Arrays.asList(tweetSummary1, tweetSummary2);
        when(interactionClient.getTweetSummaries(anyList(), eq("AUTH_TOKEN")))
                .thenReturn(CompletableFuture.completedFuture(tweetSummaryDtos));

        when(tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class)))
                .thenReturn(new TweetDto());

        CompletableFuture<List<TweetDto>> result = tweetService.getUserTweets(userId, "AUTH_TOKEN");

        assertEquals(2, result.join().size());
        verify(tweetRepository).findByUserId(userId);
        verify(interactionClient).getTweetSummaries(anyList(), eq("AUTH_TOKEN"));
        verify(tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class));
    }

    @Test
    void testGetTweet() {
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReplyDto replyDto = new ReplyDto();
        replyDto.setUserId(userId);
        replyDto.setContent("test comment");

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(tweetId);
        tweetEntity.setUserId(userId);
        when(tweetRepository.findById(tweetId)).thenReturn(Optional.of(tweetEntity));

        when(interactionClient.getLikesCount(eq(tweetEntity.getId()), any()))
                .thenReturn(CompletableFuture.completedFuture(0L));
        when(interactionClient.getRepliesCount(eq(tweetEntity.getId()), any()))
                .thenReturn(CompletableFuture.completedFuture(0L));
        when(interactionClient.getRetweetsCount(eq(tweetEntity.getId()), any()))
                .thenReturn(CompletableFuture.completedFuture(0L));
        when(interactionClient.getRepliesForTweet(eq(tweetEntity.getId()), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(replyDto)));

        TweetDto tweetDto = new TweetDto();
        when(tweetMapper.mapEntityToDto(any(TweetEntity.class), any(), any(), any(), any(List.class)))
                .thenReturn(tweetDto);

        CompletableFuture<TweetDto> result = tweetService.getTweet(tweetId, "AUTH_TOKEN");

        assertEquals(tweetDto, result.join());
    }

    @Test
    void testCreateTweet() {
        TweetCreationRequest request = new TweetCreationRequest();
        UUID tweetId = UUID.randomUUID();

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(tweetId);

        when(tweetMapper.mapCreationRequestToEntity(any())).thenReturn(tweetEntity);
        when(tweetRepository.save(any())).thenReturn(new TweetEntity());
        when(tweetMapper.mapEntityToCreationDto(any())).thenReturn(new TweetDto());

        doNothing().when(mentionService).handleTweetCreationMentions(request);
        doNothing().when(hashtagService).handleTweetCreationHashtags(request);

        CompletableFuture<TweetDto> result = tweetService.createTweet(request);

        assertNotNull(result);
        assertDoesNotThrow(() -> result.get());
        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        assertFalse(result.isCancelled());
    }

    @Test
    void testUpdateTweet() {
        UUID userId = UUID.randomUUID();
        UUID tweetId = UUID.randomUUID();
        TweetUpdateRequest request = new TweetUpdateRequest();
        request.setId(tweetId);
        request.setUserId(userId);

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(tweetId);
        tweetEntity.setUserId(userId);

        when(tweetRepository.findById(tweetId)).thenReturn(Optional.of(tweetEntity));
        when(tweetRepository.save(any())).thenReturn(tweetEntity);

        doNothing().when(mentionService).handleTweetCreationMentions(request);
        doNothing().when(hashtagService).handleTweetCreationHashtags(request);

        CompletableFuture<Void> result = tweetService.updateTweet(request);

        assertDoesNotThrow(() -> result.get());
        assertNotNull(result);
        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        assertFalse(result.isCancelled());
    }

    @Test
    void testUpdateTweetTweetNotFoundException() {
        TweetUpdateRequest request = new TweetUpdateRequest();
        UUID userId = UUID.randomUUID();
        UUID tweetId = UUID.randomUUID();

        when(tweetRepository.findByIdAndUserId(tweetId, userId)).thenReturn(java.util.Optional.empty());

        Exception e = assertThrows(CompletionException.class, () -> tweetService.updateTweet(request).join());
        assertNotNull(e.getCause());
        assertInstanceOf(TweetNotFoundException.class, e.getCause());
    }

    @Test
    void testDeleteTweet() {
        UUID tweetId = UUID.randomUUID();

        TweetEntity tweetEntity = new TweetEntity();
        when(tweetRepository.findById(tweetId)).thenReturn(Optional.of(tweetEntity));

        tweetService.deleteTweet(tweetId).join();

        verify(tweetRepository).deleteById(tweetId);
    }

    @Test
    void testSearchTweets() {
        String searchTerm = "example";
        Pageable pageable = PageRequest.of(0, 10);

        List<TweetEntity> tweetEntities = new ArrayList<>();
        when(tweetRepository.findBySimilarity(searchTerm))
                .thenReturn(new ArrayList<>(tweetEntities));

        when(tweetMapper.mapEntityToDto(any(), any())).thenReturn(new TweetDto());
        when(userService.getUserSummary(any())).thenReturn(new UserDto());

        CompletableFuture<List<TweetDto>> result = tweetService.searchTweets(searchTerm);

        assertEquals(0, result.join().size());
    }

    @Test
    void testSearchTweetsByHashtag() {
        String searchTerm = "#example";
        Pageable pageable = PageRequest.of(0, 10);

        List<TweetEntity> tweetEntities = new ArrayList<>();
        when(tweetRepository.findByHashtag(searchTerm))
                .thenReturn(new ArrayList<>(tweetEntities));

        when(tweetMapper.mapEntityToDto(any(), any())).thenReturn(new TweetDto());

        CompletableFuture<List<TweetDto>> result = tweetService.searchTweetsByHashtag(searchTerm);

        assertEquals(0, result.join().size());
    }

    @Test
    void testGetTweetSummary() {
        UUID tweetId = UUID.randomUUID();

        TweetEntity tweetEntity = new TweetEntity();
        when(tweetRepository.findById(tweetId)).thenReturn(Optional.of(tweetEntity));

        TweetDto tweetDto = new TweetDto();
        when(tweetMapper.mapEntityToDto(any())).thenReturn(tweetDto);

        CompletableFuture<TweetDto> result = tweetService.getTweetSummary(tweetId);

        assertEquals(tweetDto, result.join());
    }

    @Test
    void testGetUserTweetsSummary() {
        UUID userId = UUID.randomUUID();

        List<TweetEntity> tweetEntities = new ArrayList<>();
        when(tweetRepository.findByUserId(userId)).thenReturn(tweetEntities);

        when(tweetMapper.mapEntityToDto(any())).thenReturn(new TweetDto());

        CompletableFuture<List<TweetDto>> result = tweetService.getUserTweetsSummary(userId);

        assertEquals(0, result.join().size());
    }

    // ---------- additional branch-coverage tests ----------

    @Test
    void testGetUserFeedFallbackCacheReadValueThrows() throws Exception {
        // Covers the catch-block inside getFollowedUsersFromCache returning empty list.
        UUID userId = UUID.randomUUID();
        when(interactionClient.getFollowedIds(eq(userId)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("downstream")));
        when(valueOperations.get(anyString())).thenReturn("garbage");
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("parse-failure"));
        when(tweetRepository.findByUserIdInOrderByCreatedAtDesc(eq(new ArrayList<>())))
                .thenReturn(new ArrayList<>());
        when(interactionClient.getTweetSummaries(anyList(), eq("AUTH_TOKEN")))
                .thenReturn(CompletableFuture.completedFuture(new ArrayList<>()));

        List<TweetDto> result = tweetService.getUserFeed(userId, "AUTH_TOKEN").join();
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void testGetTweetNotFoundSurfacesAs404Cause() {
        // the exceptionally branch must rethrow TweetNotFoundException
        // rather than wrapping it in a generic TweetException.
        UUID tweetId = UUID.randomUUID();
        when(tweetRepository.findById(tweetId)).thenReturn(Optional.empty());

        Exception ex = assertThrows(CompletionException.class,
                () -> tweetService.getTweet(tweetId, "AUTH_TOKEN").join());
        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertInstanceOf(TweetNotFoundException.class, cause);
    }

    @Test
    void testGetTweetWrapsOtherExceptionAsTweetException() {
        // Covers the false branch of the cause-instanceof check — generic failures
        // (non-TweetNotFoundException) should be wrapped in TweetException.
        UUID tweetId = UUID.randomUUID();
        TweetEntity entity = new TweetEntity();
        entity.setId(tweetId);
        when(tweetRepository.findById(tweetId)).thenReturn(Optional.of(entity));

        when(interactionClient.getLikesCount(eq(tweetId), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        when(interactionClient.getRepliesCount(eq(tweetId), any()))
                .thenReturn(CompletableFuture.completedFuture(0L));
        when(interactionClient.getRetweetsCount(eq(tweetId), any()))
                .thenReturn(CompletableFuture.completedFuture(0L));
        when(interactionClient.getRepliesForTweet(eq(tweetId), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        Exception ex = assertThrows(CompletionException.class,
                () -> tweetService.getTweet(tweetId, "AUTH_TOKEN").join());
        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertInstanceOf(ro.tweebyte.tweetservice.exception.TweetException.class, cause);
    }

    @Test
    void testGetTweetSummaryNotFound() {
        UUID tweetId = UUID.randomUUID();
        when(tweetRepository.findById(tweetId)).thenReturn(Optional.empty());

        Exception ex = assertThrows(CompletionException.class,
                () -> tweetService.getTweetSummary(tweetId).join());
        assertInstanceOf(TweetNotFoundException.class, ex.getCause());
    }

    @Test
    void testSearchTweetsWithResultsExercisesPerEntityBranch() {
        // Covers the lambda branch inside computeTweetsFromPage when the page is
        // non-empty (each entity gets a userSummary lookup + map call).
        TweetEntity entity = new TweetEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());

        // Replace TweetService's `userService` field with a Mockito mock so the
        // per-entity computeTweetsFromPage lambda can run without NPEs.
        UserService userServiceMock = Mockito.mock(UserService.class);
        when(userServiceMock.getUserSummary(any(UUID.class))).thenReturn(new UserDto());
        ReflectionTestUtils.setField(tweetService, "userService", userServiceMock);

        when(tweetRepository.findBySimilarity(anyString()))
                .thenReturn(new ArrayList<>(List.of(entity)));
        when(tweetMapper.mapEntityToDto(any(TweetEntity.class), any(UserDto.class)))
                .thenReturn(new TweetDto());

        List<TweetDto> result = tweetService.searchTweets("foo").join();
        assertEquals(1, result.size());

        // Restore (other tests don't touch this field).
        ReflectionTestUtils.setField(tweetService, "userService", userService);
    }

    @Test
    void testSearchTweetsByHashtagWithResults() {
        TweetEntity entity = new TweetEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());

        UserService userServiceMock = Mockito.mock(UserService.class);
        when(userServiceMock.getUserSummary(any(UUID.class))).thenReturn(new UserDto());
        ReflectionTestUtils.setField(tweetService, "userService", userServiceMock);

        when(tweetRepository.findByHashtag(anyString()))
                .thenReturn(new ArrayList<>(List.of(entity)));
        when(tweetMapper.mapEntityToDto(any(TweetEntity.class), any(UserDto.class)))
                .thenReturn(new TweetDto());

        List<TweetDto> result = tweetService.searchTweetsByHashtag("#foo").join();
        assertEquals(1, result.size());

        ReflectionTestUtils.setField(tweetService, "userService", userService);
    }

    @Test
    void testCreateTweetTokenizationTweetNotFoundShortCircuits() throws Exception {
        // Forces the consumer call to throw TweetNotFoundException so
        // processTweetTokens hits the explicit catch-and-break branch (no retry,
        // no TweetException). Uses a dedicated single-thread executor so we can
        // join on the async tokenization tasks without affecting other tests.
        java.util.concurrent.ExecutorService localExec = Executors.newSingleThreadExecutor();
        ReflectionTestUtils.setField(tweetService, "executorService", localExec);

        TweetCreationRequest request = new TweetCreationRequest();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());

        when(tweetMapper.mapCreationRequestToEntity(any())).thenReturn(tweetEntity);
        when(tweetRepository.save(any())).thenReturn(tweetEntity);
        when(tweetMapper.mapEntityToCreationDto(any())).thenReturn(new TweetDto());

        Mockito.doThrow(new TweetNotFoundException("missing"))
                .when(mentionService).handleTweetCreationMentions(any());
        Mockito.doThrow(new TweetNotFoundException("missing"))
                .when(hashtagService).handleTweetCreationHashtags(any());

        TweetDto result = tweetService.createTweet(request).get();
        assertNotNull(result);
        localExec.shutdown();
        assertTrue(localExec.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        // Each consumer should have been invoked exactly once (break, no retry).
        verify(mentionService).handleTweetCreationMentions(any());
        verify(hashtagService).handleTweetCreationHashtags(any());

        // Restore default executor for any subsequent tests.
        ReflectionTestUtils.setField(tweetService, "executorService", executorService);
    }

    @Test
    void testCreateTweetTokenizationGenericExceptionRetriesThenWraps() throws Exception {
        // createTweet waits for both hashtag/mention handlers via
        // thenComposeAsync + allOf, so a TweetException raised inside either
        // handler propagates up the chain and surfaces as the
        // CompletableFuture's exceptional completion. Both handlers are still
        // invoked MAX_RETRIES (=10) times, exercising the generic-exception
        // catch branch + retry-exhaustion final throw.
        java.util.concurrent.ExecutorService localExec = Executors.newSingleThreadExecutor();
        ReflectionTestUtils.setField(tweetService, "executorService", localExec);

        TweetCreationRequest request = new TweetCreationRequest();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(UUID.randomUUID());

        when(tweetMapper.mapCreationRequestToEntity(any())).thenReturn(tweetEntity);
        when(tweetRepository.save(any())).thenReturn(tweetEntity);
        when(tweetMapper.mapEntityToCreationDto(any())).thenReturn(new TweetDto());

        Mockito.doThrow(new RuntimeException("flaky"))
                .when(mentionService).handleTweetCreationMentions(any());
        Mockito.doThrow(new RuntimeException("flaky"))
                .when(hashtagService).handleTweetCreationHashtags(any());

        java.util.concurrent.CompletableFuture<TweetDto> future = tweetService.createTweet(request);
        java.util.concurrent.ExecutionException thrown = org.junit.jupiter.api.Assertions
                .assertThrows(java.util.concurrent.ExecutionException.class, future::get);
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                ro.tweebyte.tweetservice.exception.TweetException.class, thrown.getCause());

        localExec.shutdown();
        assertTrue(localExec.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS));

        // 10 retries × 1 token-type per consumer.
        verify(mentionService, times(10)).handleTweetCreationMentions(any());
        verify(hashtagService, times(10)).handleTweetCreationHashtags(any());

        ReflectionTestUtils.setField(tweetService, "executorService", executorService);
    }

}