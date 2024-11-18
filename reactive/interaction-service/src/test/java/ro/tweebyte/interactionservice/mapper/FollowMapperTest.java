package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowMapperTest {

	private final FollowMapper mapper = new FollowMapperImpl();

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		String status = "ACCEPTED";

		FollowEntity followEntity = mapper.mapRequestToEntity(followerId, followedId, status);

		assertNotNull(followEntity);
		assertEquals(followerId, followEntity.getFollowerId());
		assertEquals(followedId, followEntity.getFollowedId());
		assertEquals(status, followEntity.getStatus());
		assertNotNull(followEntity.getId());
		assertNotNull(followEntity.getCreatedAt());
		assertTrue(followEntity.isInsertable());
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String status = "ACCEPTED";

		FollowEntity followEntity = FollowEntity.builder()
			.id(id)
			.followerId(followerId)
			.followedId(followedId)
			.createdAt(createdAt)
			.status(status)
			.build();

		FollowDto followDto = mapper.mapEntityToDto(followEntity);

		assertNotNull(followDto);
		assertEquals(id, followDto.getId());
		assertEquals(followerId, followDto.getFollowerId());
		assertEquals(followedId, followDto.getFollowedId());
		assertEquals(createdAt, followDto.getCreatedAt());
		assertEquals(Status.ACCEPTED, followDto.getStatus());
		assertFalse(followEntity.isNew());
	}

	@Test
	void mapEntityToDto_WithUserName_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String status = "ACCEPTED";
		String userName = "testUser";

		FollowEntity followEntity = FollowEntity.builder()
			.id(id)
			.followerId(followerId)
			.followedId(followedId)
			.createdAt(createdAt)
			.status(status)
			.build();

		FollowDto followDto = mapper.mapEntityToDto(followEntity, userName);

		assertNotNull(followDto);
		assertEquals(id, followDto.getId());
		assertEquals(followerId, followDto.getFollowerId());
		assertEquals(followedId, followDto.getFollowedId());
		assertEquals(createdAt, followDto.getCreatedAt());
		assertEquals(Status.ACCEPTED, followDto.getStatus());
		assertEquals(userName, followDto.getUserName());
	}

	@Test
	void mapEntityToDto_NullFollowEntity_ShouldReturnNull() {
		FollowDto followDto = mapper.mapEntityToDto(null);
		assertNull(followDto);
	}

	@Test
	void mapEntityToDto_WithUserName_NullFollowEntityAndUserName_ShouldReturnNull() {
		FollowDto followDto = mapper.mapEntityToDto(null, null);
		assertNull(followDto);
	}
}
