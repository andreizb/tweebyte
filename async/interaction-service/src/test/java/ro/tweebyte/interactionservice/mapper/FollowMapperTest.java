package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowDto;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowMapperTest {

	private final FollowMapper followMapper = new FollowMapperImpl();

	@Test
	void testMapRequestToEntity() {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.ACCEPTED;

		FollowEntity followEntity = followMapper.mapRequestToEntity(followerId, followedId, status);

		assertNotNull(followEntity);
		assertNotNull(followEntity.getId());
		assertNotNull(followEntity.getCreatedAt());
		assertEquals(followerId, followEntity.getFollowerId());
		assertEquals(followedId, followEntity.getFollowedId());
		assertEquals(status, followEntity.getStatus());
	}

	@Test
	void testMapEntityToDto() {
		FollowEntity followEntity = new FollowEntity();
		followEntity.setId(UUID.randomUUID());
		followEntity.setCreatedAt(LocalDateTime.now());
		followEntity.setFollowerId(UUID.randomUUID());
		followEntity.setFollowedId(UUID.randomUUID());
		followEntity.setStatus(FollowEntity.Status.ACCEPTED);
		followEntity.setCreatedAt(LocalDateTime.now());

		FollowDto followDto = followMapper.mapEntityToDto(followEntity);

		assertNotNull(followDto);
		assertEquals(followEntity.getId(), followDto.getId());
		assertEquals(followEntity.getFollowerId(), followDto.getFollowerId());
		assertEquals(followEntity.getFollowedId(), followDto.getFollowedId());
		assertEquals(followEntity.getCreatedAt(), followDto.getCreatedAt());
		assertEquals(FollowDto.Status.ACCEPTED, followDto.getStatus());
	}

	@Test
	void testMapEntityToDtoWithNullEntity() {
		FollowDto followDto = followMapper.mapEntityToDto(null);

		assertNull(followDto);
	}

	@Test
	void testMapEntityToDtoWithUserName() {
		FollowEntity followEntity = new FollowEntity();
		followEntity.setId(UUID.randomUUID());
		followEntity.setCreatedAt(LocalDateTime.now());
		followEntity.setFollowerId(UUID.randomUUID());
		followEntity.setFollowedId(UUID.randomUUID());
		followEntity.setStatus(FollowEntity.Status.PENDING);
		followEntity.setCreatedAt(LocalDateTime.now());

		String userName = "testuser";

		FollowDto followDto = followMapper.mapEntityToDto(followEntity, userName);

		assertNotNull(followDto);
		assertEquals(followEntity.getId(), followDto.getId());
		assertEquals(followEntity.getFollowerId(), followDto.getFollowerId());
		assertEquals(followEntity.getFollowedId(), followDto.getFollowedId());
		assertEquals(followEntity.getCreatedAt(), followDto.getCreatedAt());
		assertEquals(FollowDto.Status.PENDING, followDto.getStatus());
		assertEquals(userName, followDto.getUserName());
	}

	@Test
	void testMapEntityToDtoWithNullEntityAndUserName() {
		String userName = "testuser";
		FollowDto followDto = followMapper.mapEntityToDto(null, userName);

		assertNotNull(followDto);
		assertNull(followDto.getId());
		assertEquals(userName, followDto.getUserName());
	}

	@Test
	void testMapCreationRequestToEntity() {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.REJECTED;

		FollowEntity followEntity = followMapper.mapCreationRequestToEntity(followerId, followedId, status);

		assertNotNull(followEntity);
		assertEquals(followerId, followEntity.getFollowerId());
		assertEquals(followedId, followEntity.getFollowedId());
		assertEquals(status, followEntity.getStatus());
	}

}
