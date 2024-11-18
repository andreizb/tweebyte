package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class FollowServiceTest {

    @InjectMocks
    private FollowService followService;

    @Mock
    private UserService userService;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FollowMapper followMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOperations;

    @Mock
    private ReactiveListOperations<String, Object> listOperations;

    private final UUID userId = UUID.randomUUID();
    private final UUID followedId = UUID.randomUUID();
    private final String userName = "testUser";
    private final FollowEntity followEntity = new FollowEntity();
    private final UserDto userDto = new UserDto();
    private final FollowDto followDto = new FollowDto();

    @BeforeEach
    public void init() {
        userDto.setId(userId);
        userDto.setUserName(userName);

        valueOperations = mock(ReactiveValueOperations.class);
        listOperations = mock(ReactiveListOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }
    
    @Test
    public void getFollowersCount_Success() {
        when(followRepository.countByFollowedIdAndStatus(any(UUID.class), eq("ACCEPTED")))
            .thenReturn(Mono.just(10L));

        StepVerifier.create(followService.getFollowersCount(userId))
            .expectNext(10L)
            .verifyComplete();
    }

    @Test
    public void getFollowingCount_Success() {
        when(followRepository.countByFollowerIdAndStatus(any(UUID.class), eq("ACCEPTED")))
            .thenReturn(Mono.just(5L));

        StepVerifier.create(followService.getFollowingCount(userId))
            .expectNext(5L)
            .verifyComplete();
    }

    @Test
    public void getFollowers_Success() {
        FollowEntity followEntity = new FollowEntity();
        followEntity.setFollowerId(UUID.randomUUID());
        followEntity.setFollowedId(userId);
        followEntity.setStatus("ACCEPTED");

        UserDto userDto = new UserDto();
        userDto.setId(followEntity.getFollowerId());
        userDto.setUserName(userName);

        FollowDto followDto = new FollowDto();
        followDto.setFollowerId(followEntity.getFollowerId());
        followDto.setFollowedId(followEntity.getFollowedId());
        followDto.setStatus(Status.ACCEPTED);

        when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(any(UUID.class), eq("ACCEPTED")))
            .thenReturn(Flux.just(followEntity));
        when(userService.getUserSummary(followEntity.getFollowerId()))
            .thenReturn(Mono.just(userDto));
        when(followMapper.mapEntityToDto(followEntity, userName))
            .thenReturn(followDto);

        StepVerifier.create(followService.getFollowers(userId, "authToken"))
            .expectNext(followDto)
            .verifyComplete();
    }

    @Test
    public void follow_Success() {
        when(userService.getUserSummary(any(UUID.class)))
            .thenReturn(Mono.just(userDto));
        when(followMapper.mapRequestToEntity(any(UUID.class), any(UUID.class), anyString()))
            .thenReturn(followEntity);
        when(followRepository.save(any(FollowEntity.class)))
            .thenReturn(Mono.just(followEntity));
        when(followMapper.mapEntityToDto(any(FollowEntity.class)))
            .thenReturn(followDto);

        StepVerifier.create(followService.follow(userId, followedId))
            .expectNext(followDto)
            .verifyComplete();
    }

    @Test
    public void unfollow_Success() {
        when(followRepository.deleteByFollowerIdAndFollowedId(any(UUID.class), any(UUID.class)))
            .thenReturn(Mono.empty());

        StepVerifier.create(followService.unfollow(userId, followedId))
            .verifyComplete();
    }

    @Test
    public void updateFollowRequest_Success() {
        UUID followRequestId = UUID.randomUUID();
        UUID followerId = userId;

        FollowEntity followEntity = new FollowEntity();
        followEntity.setId(followRequestId);
        followEntity.setFollowerId(UUID.randomUUID());
        followEntity.setFollowedId(followerId);
        followEntity.setStatus(Status.PENDING.name());

        when(followRepository.findById(followRequestId)).thenReturn(Mono.just(followEntity));
        when(followRepository.save(any(FollowEntity.class))).thenReturn(Mono.just(followEntity));

        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("follow_recommendations")).thenReturn(mockCache);
        doNothing().when(mockCache).evict(userId.toString());

        StepVerifier.create(followService.updateFollowRequest(followerId, followRequestId, Status.ACCEPTED))
            .verifyComplete();

        verify(followRepository).findById(followRequestId);
        verify(followRepository).save(any(FollowEntity.class));
    }

    @Test
    public void getFollowedIdentifiers_Success() {
        UUID followedId = UUID.randomUUID();
        FollowEntity followEntity = new FollowEntity();
        followEntity.setFollowedId(followedId);

        when(followRepository.findByFollowerIdAndStatus(any(UUID.class), eq("ACCEPTED")))
            .thenReturn(Flux.just(followEntity));

        StepVerifier.create(followService.getFollowedIdentifiers(userId))
            .expectNext(followedId)
            .verifyComplete();
    }

}
