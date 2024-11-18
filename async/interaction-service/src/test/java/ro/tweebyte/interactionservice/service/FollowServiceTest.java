package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.FollowNotFoundException;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class FollowServiceTest {

	@Mock
	private FollowRepository followRepository;

	@Mock
	private FollowMapper followMapper;

	@Mock
	private CacheManager cacheManager;

	@InjectMocks
	private FollowService followService;

	@Mock
	private ObjectMapper objectMapper;

	@Test
	void testGetFollowers() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(eq(userId), eq(FollowEntity.Status.ACCEPTED)))
			.thenReturn(followEntities);
		when(followMapper.mapEntityToDto(any(FollowEntity.class), eq("some user"))).thenReturn(new FollowDto());

		List<FollowDto> result = followService.getFollowers(userId).get();

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(followRepository).findByFollowedIdAndStatusOrderByCreatedAtDesc(eq(userId), eq(FollowEntity.Status.ACCEPTED));
		verify(followMapper).mapEntityToDto(any(FollowEntity.class), eq("some user"));
	}

	@Test
	void testGetFollowing() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		when(followRepository.findByFollowerIdAndStatus(eq(userId), eq(FollowEntity.Status.ACCEPTED)))
			.thenReturn(followEntities);
		when(followMapper.mapEntityToDto(any(FollowEntity.class), eq("some user"))).thenReturn(new FollowDto());

		List<FollowDto> result = followService.getFollowing(userId).get();

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(followRepository).findByFollowerIdAndStatus(eq(userId), eq(FollowEntity.Status.ACCEPTED));
		verify(followMapper).mapEntityToDto(any(FollowEntity.class), eq("some user"));
	}

	@Test
	void testGetFollowersCount() throws Exception {
		UUID userId = UUID.randomUUID();
		when(followRepository.countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED)).thenReturn(5L);

		Long count = followService.getFollowersCount(userId).get();

		assertEquals(5L, count);
		verify(followRepository).countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED);
	}

	@Test
	void testGetFollowingCount() throws Exception {
		UUID userId = UUID.randomUUID();
		when(followRepository.countByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED)).thenReturn(3L);

		Long count = followService.getFollowingCount(userId).get();

		assertEquals(3L, count);
		verify(followRepository).countByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED);
	}

	@Test
	void testGetFollowRequests() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		when(followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(eq(userId), eq(FollowEntity.Status.PENDING)))
			.thenReturn(followEntities);
		when(followMapper.mapEntityToDto(followEntity)).thenReturn(new FollowDto());

		List<FollowDto> result = followService.getFollowRequests(userId).get();

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(followRepository).findByFollowedIdAndStatusOrderByCreatedAtDesc(eq(userId), eq(FollowEntity.Status.PENDING));
		verify(followMapper).mapEntityToDto(any(FollowEntity.class));
	}

	@Test
	void testFollow() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();

		when(followMapper.mapRequestToEntity(userId, followedId, FollowEntity.Status.PENDING)).thenReturn(followEntity);
		when(followRepository.save(followEntity)).thenReturn(followEntity);
		when(followMapper.mapEntityToDto(followEntity)).thenReturn(new FollowDto());

		FollowDto result = followService.follow(userId, followedId).get();

		assertNotNull(result);

		verify(followMapper).mapRequestToEntity(userId, followedId, FollowEntity.Status.PENDING);
		verify(followRepository).save(followEntity);
		verify(followMapper).mapEntityToDto(followEntity);
	}

	@Test
	void testUpdateFollowRequest() throws Exception {
		UUID followRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowerId(UUID.randomUUID());
		followEntity.setFollowedId(userId);
		followEntity.setStatus(FollowEntity.Status.PENDING);

		when(followRepository.findById(followRequestId)).thenReturn(Optional.of(followEntity));
		when(followRepository.save(any(FollowEntity.class))).thenReturn(followEntity);

		followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.ACCEPTED).get();

		verify(followRepository).findById(followRequestId);
		verify(followRepository).save(any(FollowEntity.class));
	}

	@Test
	void testUpdateFollowRequestThrowsException() {
		UUID followRequestId = UUID.randomUUID();
		when(followRepository.findById(followRequestId)).thenReturn(Optional.empty());

		CompletableFuture<Void> result = followService.updateFollowRequest(UUID.randomUUID(), followRequestId, FollowEntity.Status.ACCEPTED);

		assertThrows(ExecutionException.class, result::get);
	}

	@Test
	void testUnfollow() throws Exception {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();

		doNothing().when(followRepository).deleteByFollowerIdAndFollowedId(followerId, followedId);

		followService.unfollow(followerId, followedId).get();

		verify(followRepository).deleteByFollowerIdAndFollowedId(followerId, followedId);
	}

	@Test
	void testGetFollowedIdentifiers() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowedId(UUID.randomUUID());
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		when(followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED)).thenReturn(followEntities);

		List<UUID> result = followService.getFollowedIdentifiers(userId).get();

		assertNotNull(result);
		assertEquals(1, result.size());
		verify(followRepository).findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED);
	}
}
