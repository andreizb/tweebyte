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

	private final LikeMapper likeMapper = new LikeMapperImpl();

	@Test
	void testMapRequestToEntity() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.TWEET;

		LikeEntity likeEntity = likeMapper.mapRequestToEntity(userId, likeableId, likeableType);

		assertNotNull(likeEntity);
		assertNotNull(likeEntity.getId());
		assertNotNull(likeEntity.getCreatedAt());
		assertEquals(userId, likeEntity.getUserId());
		assertEquals(likeableId, likeEntity.getLikeableId());
		assertEquals(likeableType, likeEntity.getLikeableType());
	}

	@Test
	void testMapEntityToDto() {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setId(UUID.randomUUID());
		likeEntity.setCreatedAt(LocalDateTime.now());

		LikeDto likeDto = likeMapper.mapEntityToDto(likeEntity);

		assertNotNull(likeDto);
		assertEquals(likeEntity.getId(), likeDto.getId());
		assertEquals(likeEntity.getCreatedAt(), likeDto.getCreatedAt());
	}

	@Test
	void testMapEntityToDtoWithNullEntity() {
		LikeDto likeDto = likeMapper.mapEntityToDto(null);

		assertNull(likeDto);
	}

	@Test
	void testMapToDtoWithUserDto() {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setId(UUID.randomUUID());
		likeEntity.setCreatedAt(LocalDateTime.now());

		UserDto userDto = new UserDto(UUID.randomUUID(), "username", true, LocalDateTime.now());

		LikeDto likeDto = likeMapper.mapToDto(likeEntity, userDto);

		assertNotNull(likeDto);
		assertEquals(likeEntity.getId(), likeDto.getId());
		assertEquals(likeEntity.getCreatedAt(), likeDto.getCreatedAt());
		assertEquals(userDto, likeDto.getUser());
	}

	@Test
	void testMapToDtoWithTweetDto() {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setId(UUID.randomUUID());
		likeEntity.setCreatedAt(LocalDateTime.now());

		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(UUID.randomUUID());
		tweetDto.setContent("Test Tweet");

		LikeDto likeDto = likeMapper.mapToDto(likeEntity, tweetDto);

		assertNotNull(likeDto);
		assertEquals(likeEntity.getId(), likeDto.getId());
		assertEquals(likeEntity.getCreatedAt(), likeDto.getCreatedAt());
		assertEquals(tweetDto, likeDto.getTweet());
	}

	@Test
	void testMapCreationRequestToEntity() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.TWEET;

		LikeEntity likeEntity = likeMapper.mapCreationRequestToEntity(userId, likeableId, likeableType);

		assertNotNull(likeEntity);
		assertEquals(userId, likeEntity.getUserId());
		assertEquals(likeableId, likeEntity.getLikeableId());
		assertEquals(likeableType, likeEntity.getLikeableType());
	}

	@Test
	void testMapCreationRequestToEntityWithNullValues() {
		LikeEntity likeEntity = likeMapper.mapCreationRequestToEntity(null, null, null);

		assertNull(likeEntity);
	}
}
