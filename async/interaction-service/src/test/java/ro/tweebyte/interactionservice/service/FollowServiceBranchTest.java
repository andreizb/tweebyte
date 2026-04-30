package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage tests for FollowService — exercises:
 *  - getFollowing cache-hit vs cache-miss arms
 *  - getFollowing JSON-serialise failure path
 *  - follow() public/private toggle
 *  - updateFollowRequest invalid-status branches
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowServiceBranchTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FollowMapper followMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private UserService userService;

    @Mock
    private ExecutorService executorService;

    @Mock
    private RedisTemplate<String, byte[]> redisTemplate;

    @Mock
    private ValueOperations<String, byte[]> valueOperations;

    @InjectMocks
    private FollowService followService;

    @BeforeEach
    void setUp() {
        // Make supplyAsync(...) on this executor run synchronously.
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
    }

    @Test
    void getFollowing_cacheMiss_writesToRedis() throws Exception {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // cache miss
        when(followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
            .thenReturn(Collections.emptyList());

        byte[] result = followService.getFollowing(userId).get();

        assertNotNull(result);
        verify(valueOperations).set(anyString(), any(byte[].class), any());
    }

    @Test
    void getFollowing_emptyByteArray_treatedAsMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(new byte[0]); // length=0 path
        when(followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
            .thenReturn(Collections.emptyList());

        byte[] result = followService.getFollowing(userId).get();

        assertNotNull(result);
        verify(valueOperations).set(anyString(), any(byte[].class), any());
    }

    @Test
    void getFollowing_serializerFails_propagates() throws Exception {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        FollowEntity entity = new FollowEntity();
        when(followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
            .thenReturn(List.of(entity));
        when(followMapper.mapEntityToDto(any(FollowEntity.class), eq("some user")))
            .thenReturn(new FollowDto());

        // Inject a mock ObjectMapper that throws on writeValueAsBytes.
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.writeValueAsBytes(any())).thenThrow(new JsonProcessingException("boom") {});
        ReflectionTestUtils.setField(followService, "objectMapper", mockMapper);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> followService.getFollowing(userId).get());
        assertInstanceOf(RuntimeException.class, ex.getCause());
    }

    @Test
    void follow_privateUser_pendingStatus() throws Exception {
        // Ternary branch: isPrivate=true → status PENDING, not ACCEPTED.
        UUID userId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();

        UserDto userDto = new UserDto();
        userDto.setIsPrivate(true);
        when(userService.getUserSummary(followedId)).thenReturn(userDto);

        FollowEntity entity = new FollowEntity();
        when(followMapper.mapRequestToEntity(userId, followedId, FollowEntity.Status.PENDING))
            .thenReturn(entity);
        when(followRepository.save(entity)).thenReturn(entity);
        when(followMapper.mapEntityToDto(entity)).thenReturn(new FollowDto());

        followService.follow(userId, followedId).get();

        verify(followMapper).mapRequestToEntity(userId, followedId, FollowEntity.Status.PENDING);
        verify(followMapper, never()).mapRequestToEntity(any(), any(), eq(FollowEntity.Status.ACCEPTED));
    }

    @Test
    void updateFollowRequest_pendingStatus_throws() {
        // Negative branch: trying to "update" to PENDING is rejected.
        UUID followRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FollowEntity entity = new FollowEntity();
        entity.setFollowerId(userId);
        when(followRepository.findById(followRequestId)).thenReturn(Optional.of(entity));

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.PENDING).get());
        assertInstanceOf(RuntimeException.class, ex.getCause());
        verify(followRepository, never()).save(any());
    }

    @Test
    void updateFollowRequest_followerEqualsUserAndAccepted_throws() {
        // Negative branch: user accepting their own request → reject.
        UUID followRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FollowEntity entity = new FollowEntity();
        entity.setFollowerId(userId); // same as caller
        when(followRepository.findById(followRequestId)).thenReturn(Optional.of(entity));

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.ACCEPTED).get());
        assertInstanceOf(RuntimeException.class, ex.getCause());
        verify(followRepository, never()).save(any());
    }

    @Test
    void updateFollowRequest_followerEqualsUserButRejected_savesEntity() throws Exception {
        // Branch: equals(userId) true, status == ACCEPTED false (REJECTED) → predicate false → save.
        UUID followRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FollowEntity entity = new FollowEntity();
        entity.setFollowerId(userId); // same as caller
        when(followRepository.findById(followRequestId)).thenReturn(Optional.of(entity));

        followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.REJECTED).get();

        verify(followRepository).save(entity);
        assertEquals(FollowEntity.Status.REJECTED, entity.getStatus());
    }

    @Test
    void updateFollowRequest_rejected_savesEntity() throws Exception {
        // Positive: REJECTED status with different follower → succeeds.
        UUID followRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FollowEntity entity = new FollowEntity();
        entity.setFollowerId(UUID.randomUUID()); // different
        when(followRepository.findById(followRequestId)).thenReturn(Optional.of(entity));

        followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.REJECTED).get();

        verify(followRepository).save(entity);
        assertEquals(FollowEntity.Status.REJECTED, entity.getStatus());
    }
}
