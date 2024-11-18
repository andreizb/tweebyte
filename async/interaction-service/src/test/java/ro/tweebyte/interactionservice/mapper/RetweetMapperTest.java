package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetweetMapperTest {

	private final RetweetMapper retweetMapper = new RetweetMapperImpl();

	@Test
	void testMapRequestToEntity() {
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(UUID.randomUUID());
		request.setRetweeterId(UUID.randomUUID());
		request.setContent("This is a retweet.");

		RetweetEntity retweetEntity = retweetMapper.mapRequestToEntity(request);

		assertNotNull(retweetEntity);
		assertNotNull(retweetEntity.getId());
		assertNotNull(retweetEntity.getCreatedAt());
		assertEquals(request.getOriginalTweetId(), retweetEntity.getOriginalTweetId());
		assertEquals(request.getRetweeterId(), retweetEntity.getRetweeterId());
		assertEquals(request.getContent(), retweetEntity.getContent());
	}

	@Test
	void testMapEntityToDto() {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setContent("This is a retweet.");
		retweetEntity.setCreatedAt(LocalDateTime.now());

		RetweetDto retweetDto = retweetMapper.mapEntityToDto(retweetEntity);

		assertNotNull(retweetDto);
		assertEquals(retweetEntity.getId(), retweetDto.getId());
		assertEquals(retweetEntity.getContent(), retweetDto.getContent());
		assertEquals(retweetEntity.getCreatedAt(), retweetDto.getCreatedAt());
	}

	@Test
	void testMapEntityToDtoWithUser() {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setContent("This is a retweet.");
		retweetEntity.setCreatedAt(LocalDateTime.now());

		UserDto user = new UserDto(UUID.randomUUID(), "testuser", true, LocalDateTime.now());

		RetweetDto retweetDto = retweetMapper.mapEntityToDto(retweetEntity, user);

		assertNotNull(retweetDto);
		assertEquals(retweetEntity.getId(), retweetDto.getId());
		assertEquals(retweetEntity.getContent(), retweetDto.getContent());
		assertEquals(retweetEntity.getCreatedAt(), retweetDto.getCreatedAt());
		assertEquals(user, retweetDto.getUser());
	}

	@Test
	void testMapEntityToDtoWithUserAndTweet() {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setContent("This is a retweet.");
		retweetEntity.setCreatedAt(LocalDateTime.now());

		UserDto user = new UserDto(UUID.randomUUID(), "testuser", true, LocalDateTime.now());
		TweetDto tweet = new TweetDto(UUID.randomUUID(), UUID.randomUUID(), "Tweet content", null, null, null, null, null, null, null);

		RetweetDto retweetDto = retweetMapper.mapEntityToDto(retweetEntity, user, tweet);

		assertNotNull(retweetDto);
		assertEquals(retweetEntity.getId(), retweetDto.getId());
		assertEquals(retweetEntity.getContent(), retweetDto.getContent());
		assertEquals(retweetEntity.getCreatedAt(), retweetDto.getCreatedAt());
		assertEquals(user, retweetDto.getUser());
		assertEquals(tweet, retweetDto.getTweet());
	}

	@Test
	void testMapRequestToEntityForUpdate() {
		RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();
		updateRequest.setId(UUID.randomUUID());
		updateRequest.setRetweeterId(UUID.randomUUID());
		updateRequest.setContent("Updated content.");

		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setRetweeterId(UUID.randomUUID());
		retweetEntity.setContent("Old content.");

		retweetMapper.mapRequestToEntity(updateRequest, retweetEntity);

		assertNotNull(retweetEntity);
		assertEquals(updateRequest.getId(), retweetEntity.getId());
		assertEquals(updateRequest.getRetweeterId(), retweetEntity.getRetweeterId());
		assertEquals(updateRequest.getContent(), retweetEntity.getContent());
	}

	@Test
	void testMapCreationRequestToEntity() {
		RetweetCreateRequest createRequest = new RetweetCreateRequest();
		createRequest.setOriginalTweetId(UUID.randomUUID());
		createRequest.setRetweeterId(UUID.randomUUID());
		createRequest.setContent("Create request content.");

		RetweetEntity retweetEntity = retweetMapper.mapCreationRequestToEntity(createRequest);

		assertNotNull(retweetEntity);
		assertEquals(createRequest.getOriginalTweetId(), retweetEntity.getOriginalTweetId());
		assertEquals(createRequest.getRetweeterId(), retweetEntity.getRetweeterId());
		assertEquals(createRequest.getContent(), retweetEntity.getContent());
	}

	@Test
	void testMapCreationRequestToEntityWithNullRequest() {
		RetweetEntity retweetEntity = retweetMapper.mapCreationRequestToEntity(null);

		assertNull(retweetEntity);
	}
}
