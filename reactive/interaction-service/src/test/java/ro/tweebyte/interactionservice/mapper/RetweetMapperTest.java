package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetweetMapperTest {

	private final RetweetMapper mapper = new RetweetMapperImpl();

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "Retweet content";

		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(originalTweetId);
		request.setRetweeterId(retweeterId);
		request.setContent(content);

		RetweetEntity retweetEntity = mapper.mapRequestToEntity(request);

		assertNotNull(retweetEntity);
		assertEquals(originalTweetId, retweetEntity.getOriginalTweetId());
		assertEquals(retweeterId, retweetEntity.getRetweeterId());
		assertEquals(content, retweetEntity.getContent());
		assertNotNull(retweetEntity.getId());
		assertNotNull(retweetEntity.getCreatedAt());
		assertTrue(retweetEntity.isInsertable());
		assertTrue(retweetEntity.isNew());
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		String content = "Retweet content";
		LocalDateTime createdAt = LocalDateTime.now();

		RetweetEntity retweetEntity = RetweetEntity.builder()
			.id(id)
			.content(content)
			.createdAt(createdAt)
			.build();

		RetweetDto retweetDto = mapper.mapEntityToDto(retweetEntity);

		assertNotNull(retweetDto);
		assertEquals(id, retweetDto.getId());
		assertEquals(content, retweetDto.getContent());
		assertEquals(createdAt, retweetDto.getCreatedAt());
	}

	@Test
	void mapEntityToDto_WithUser_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		String content = "Retweet content";
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setId(UUID.randomUUID());

		RetweetEntity retweetEntity = RetweetEntity.builder()
			.id(id)
			.content(content)
			.createdAt(createdAt)
			.build();

		RetweetDto retweetDto = mapper.mapEntityToDto(retweetEntity, userDto);

		assertNotNull(retweetDto);
		assertEquals(id, retweetDto.getId());
		assertEquals(content, retweetDto.getContent());
		assertEquals(createdAt, retweetDto.getCreatedAt());
		assertEquals(userDto, retweetDto.getUser());
	}

	@Test
	void mapEntityToDto_WithUserAndTweet_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		String content = "Retweet content";
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setId(UUID.randomUUID());
		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(UUID.randomUUID());

		RetweetEntity retweetEntity = RetweetEntity.builder()
			.id(id)
			.content(content)
			.createdAt(createdAt)
			.build();

		RetweetDto retweetDto = mapper.mapEntityToDto(retweetEntity, userDto, tweetDto);

		assertNotNull(retweetDto);
		assertEquals(id, retweetDto.getId());
		assertEquals(content, retweetDto.getContent());
		assertEquals(createdAt, retweetDto.getCreatedAt());
		assertEquals(userDto, retweetDto.getUser());
		assertEquals(tweetDto, retweetDto.getTweet());
	}

	@Test
	void mapRequestToEntity_Update_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String updatedContent = "Updated retweet content";

		RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();
		updateRequest.setId(id);
		updateRequest.setRetweeterId(retweeterId);
		updateRequest.setContent(updatedContent);

		RetweetEntity retweetEntity = new RetweetEntity();
		mapper.mapRequestToEntity(updateRequest, retweetEntity);

		assertNotNull(retweetEntity);
		assertEquals(id, retweetEntity.getId());
		assertEquals(retweeterId, retweetEntity.getRetweeterId());
		assertEquals(updatedContent, retweetEntity.getContent());
	}

	@Test
	void mapCreationRequestToEntity_NullRequest_ShouldReturnNull() {
		RetweetEntity retweetEntity = mapper.mapCreationRequestToEntity(null);
		assertNull(retweetEntity);
	}

	@Test
	void mapEntityToDto_NullEntity_ShouldReturnNull() {
		RetweetDto retweetDto = mapper.mapEntityToDto(null);
		assertNull(retweetDto);
	}

	@Test
	void mapRequestToEntity_NullUpdateRequest_ShouldNotModifyEntity() {
		RetweetEntity retweetEntity = new RetweetEntity();
		mapper.mapRequestToEntity(null, retweetEntity);

		assertNotNull(retweetEntity);
	}
}
