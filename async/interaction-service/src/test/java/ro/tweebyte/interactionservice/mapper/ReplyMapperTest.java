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

	private final ReplyMapper replyMapper = new ReplyMapperImpl();

	@Test
	void testMapRequestToEntity() {
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(UUID.randomUUID());
		request.setUserId(UUID.randomUUID());
		request.setContent("This is a reply.");

		ReplyEntity replyEntity = replyMapper.mapRequestToEntity(request);

		assertNotNull(replyEntity);
		assertNotNull(replyEntity.getId());
		assertNotNull(replyEntity.getCreatedAt());
		assertEquals(request.getTweetId(), replyEntity.getTweetId());
		assertEquals(request.getUserId(), replyEntity.getUserId());
		assertEquals(request.getContent(), replyEntity.getContent());
	}

	@Test
	void testMapEntityToCreationDto() {
		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setCreatedAt(LocalDateTime.now());
		replyEntity.setTweetId(UUID.randomUUID());
		replyEntity.setUserId(UUID.randomUUID());
		replyEntity.setContent("This is a reply.");

		ReplyDto replyDto = replyMapper.mapEntityToCreationDto(replyEntity);

		assertNotNull(replyDto);
		assertEquals(replyEntity.getId(), replyDto.getId());
	}

	@Test
	void testMapEntityToCreationDtoWithNullEntity() {
		ReplyDto replyDto = replyMapper.mapEntityToCreationDto(null);

		assertNull(replyDto);
	}

	@Test
	void testMapEntityToDtoWithUserName() {
		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setCreatedAt(LocalDateTime.now());
		replyEntity.setTweetId(UUID.randomUUID());
		replyEntity.setUserId(UUID.randomUUID());
		replyEntity.setContent("This is a reply.");

		String userName = "testUser";

		ReplyDto replyDto = replyMapper.mapEntityToDto(replyEntity, userName);

		assertNotNull(replyDto);
		assertEquals(replyEntity.getId(), replyDto.getId());
		assertEquals(replyEntity.getUserId(), replyDto.getUserId());
		assertEquals(replyEntity.getContent(), replyDto.getContent());
		assertEquals(replyEntity.getCreatedAt(), replyDto.getCreatedAt());
		assertEquals(userName, replyDto.getUserName());
	}

	@Test
	void testMapEntityToDtoWithNullValues() {
		ReplyDto replyDto = replyMapper.mapEntityToDto(null, null);

		assertNull(replyDto);
	}

	@Test
	void testMapRequestToEntityForUpdate() {
		ReplyUpdateRequest updateRequest = new ReplyUpdateRequest();
		updateRequest.setId(UUID.randomUUID());
		updateRequest.setUserId(UUID.randomUUID());
		updateRequest.setContent("Updated content.");

		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setUserId(UUID.randomUUID());
		replyEntity.setContent("Old content.");

		replyMapper.mapRequestToEntity(updateRequest, replyEntity);

		assertNotNull(replyEntity);
		assertEquals(updateRequest.getId(), replyEntity.getId());
		assertEquals(updateRequest.getUserId(), replyEntity.getUserId());
		assertEquals(updateRequest.getContent(), replyEntity.getContent());
	}

	@Test
	void testMapCreationRequestToEntity() {
		ReplyCreateRequest createRequest = new ReplyCreateRequest();
		createRequest.setTweetId(UUID.randomUUID());
		createRequest.setUserId(UUID.randomUUID());
		createRequest.setContent("Create request content.");

		ReplyEntity replyEntity = replyMapper.mapCreationRequestToEntity(createRequest);

		assertNotNull(replyEntity);
		assertEquals(createRequest.getTweetId(), replyEntity.getTweetId());
		assertEquals(createRequest.getUserId(), replyEntity.getUserId());
		assertEquals(createRequest.getContent(), replyEntity.getContent());
	}

	@Test
	void testMapCreationRequestToEntityWithNullRequest() {
		ReplyEntity replyEntity = replyMapper.mapCreationRequestToEntity(null);

		assertNull(replyEntity);
	}
}
