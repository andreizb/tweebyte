package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplyMapperTest {

	private final ReplyMapper mapper = new ReplyMapperImpl();

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test reply.";

		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(tweetId);
		request.setUserId(userId);
		request.setContent(content);

		ReplyEntity replyEntity = mapper.mapRequestToEntity(request);

		assertNotNull(replyEntity);
		assertEquals(tweetId, replyEntity.getTweetId());
		assertEquals(userId, replyEntity.getUserId());
		assertEquals(content, replyEntity.getContent());
		assertNotNull(replyEntity.getId());
		assertNotNull(replyEntity.getCreatedAt());
		assertTrue(replyEntity.isInsertable());
		assertTrue(replyEntity.isNew());
	}

	@Test
	void mapEntityToCreationDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();

		ReplyEntity replyEntity = ReplyEntity.builder()
			.id(id)
			.build();

		ReplyDto replyDto = mapper.mapEntityToCreationDto(replyEntity);

		assertNotNull(replyDto);
		assertEquals(id, replyDto.getId());
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a reply.";
		LocalDateTime createdAt = LocalDateTime.now();
		String userName = "testUser";

		ReplyEntity replyEntity = ReplyEntity.builder()
			.id(id)
			.userId(userId)
			.content(content)
			.createdAt(createdAt)
			.build();

		ReplyDto replyDto = mapper.mapEntityToDto(replyEntity, userName);

		assertNotNull(replyDto);
		assertEquals(id, replyDto.getId());
		assertEquals(userId, replyDto.getUserId());
		assertEquals(content, replyDto.getContent());
		assertEquals(createdAt, replyDto.getCreatedAt());
		assertEquals(userName, replyDto.getUserName());
	}

	@Test
	void mapRequestToEntity_Update_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String updatedContent = "Updated content";

		ReplyUpdateRequest updateRequest = new ReplyUpdateRequest();
		updateRequest.setId(id);
		updateRequest.setUserId(userId);
		updateRequest.setContent(updatedContent);

		ReplyEntity replyEntity = new ReplyEntity();
		mapper.mapRequestToEntity(updateRequest, replyEntity);

		assertNotNull(replyEntity);
		assertEquals(id, replyEntity.getId());
		assertEquals(userId, replyEntity.getUserId());
		assertEquals(updatedContent, replyEntity.getContent());
	}

	@Test
	void mapCreationRequestToEntity_NullRequest_ShouldReturnNull() {
		ReplyEntity replyEntity = mapper.mapCreationRequestToEntity(null);
		assertNull(replyEntity);
	}

	@Test
	void mapEntityToDto_NullEntity_ShouldReturnNull() {
		ReplyDto replyDto = mapper.mapEntityToCreationDto(null);
		assertNull(replyDto);
	}

	@Test
	void mapRequestToEntity_NullUpdateRequest_ShouldNotModifyEntity() {
		ReplyEntity replyEntity = new ReplyEntity();
		mapper.mapRequestToEntity(null, replyEntity);

		assertNotNull(replyEntity);
	}
}
