package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LikeMapperTest {

	private final LikeMapper mapper = new LikeMapperImpl();

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		String likeableType = "TWEET";

		LikeEntity likeEntity = mapper.mapRequestToEntity(userId, likeableId, likeableType);

		assertNotNull(likeEntity);
		assertEquals(userId, likeEntity.getUserId());
		assertEquals(likeableId, likeEntity.getLikeableId());
		assertEquals(likeableType, likeEntity.getLikeableType());
		assertNotNull(likeEntity.getId());
		assertNotNull(likeEntity.getCreatedAt());
		assertTrue(likeEntity.isInsertable());
		assertTrue(likeEntity.isNew());
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		LikeEntity likeEntity = LikeEntity.builder()
			.id(id)
			.createdAt(createdAt)
			.build();

		LikeDto likeDto = mapper.mapEntityToDto(likeEntity);

		assertNotNull(likeDto);
		assertEquals(id, likeDto.getId());
		assertEquals(createdAt, likeDto.getCreatedAt());
	}

	@Test
	void mapToDto_WithUserDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setId(UUID.randomUUID());

		LikeEntity likeEntity = LikeEntity.builder()
			.id(id)
			.createdAt(createdAt)
			.build();

		LikeDto likeDto = mapper.mapToDto(likeEntity, userDto);

		assertNotNull(likeDto);
		assertEquals(id, likeDto.getId());
		assertEquals(createdAt, likeDto.getCreatedAt());
		assertEquals(userDto, likeDto.getUser());
	}

	@Test
	void mapToDto_WithTweetDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(UUID.randomUUID());

		LikeEntity likeEntity = LikeEntity.builder()
			.id(id)
			.createdAt(createdAt)
			.build();

		LikeDto likeDto = mapper.mapToDto(likeEntity, tweetDto);

		assertNotNull(likeDto);
		assertEquals(id, likeDto.getId());
		assertEquals(createdAt, likeDto.getCreatedAt());
		assertEquals(tweetDto, likeDto.getTweet());
	}

	@Test
	void mapEntityToDto_NullEntity_ShouldReturnNull() {
		LikeDto likeDto = mapper.mapEntityToDto(null);
		assertNull(likeDto);
	}

	@Test
	void mapRequestToEntity_NullInput_ShouldReturnNull() {
		LikeEntity likeEntity = mapper.mapCreationRequestToEntity(null, null, null);
		assertNull(likeEntity);
	}
}
