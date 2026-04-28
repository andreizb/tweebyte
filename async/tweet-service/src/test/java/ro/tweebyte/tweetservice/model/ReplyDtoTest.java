package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplyDtoTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
	}

	@Test
	void testLombokGeneratedMethods() {
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		List<LikeDto> likes = List.of(new LikeDto(UUID.randomUUID()));

		ReplyDto replyDto1 = ReplyDto.builder()
			.id(replyId)
			.userId(userId)
			.userName("test_user")
			.content("This is a reply")
			.createdAt(createdAt)
			.likesCount(5L)
			.likes(likes)
			.build();

		ReplyDto replyDto2 = ReplyDto.builder()
			.id(replyId)
			.userId(userId)
			.userName("test_user")
			.content("This is a reply")
			.createdAt(createdAt)
			.likesCount(5L)
			.likes(likes)
			.build();

		assertEquals(replyDto1, replyDto2);
		assertEquals(replyDto1.hashCode(), replyDto2.hashCode());
		assertNotNull(replyDto1.toString());
		assertTrue(replyDto1.toString().contains("ReplyDto"));

		assertEquals(replyId, replyDto1.getId());
		assertEquals(userId, replyDto1.getUserId());
		assertEquals("test_user", replyDto1.getUserName());
		assertEquals("This is a reply", replyDto1.getContent());
		assertEquals(createdAt, replyDto1.getCreatedAt());
		assertEquals(5L, replyDto1.getLikesCount());
		assertEquals(likes, replyDto1.getLikes());
	}

	@Test
	void testBuilderPattern() {
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		List<LikeDto> likes = List.of(new LikeDto(UUID.randomUUID()));

		ReplyDto replyDto = ReplyDto.builder()
			.id(replyId)
			.userId(userId)
			.userName("test_user")
			.content("This is a reply")
			.createdAt(createdAt)
			.likesCount(10L)
			.likes(likes)
			.build();

		assertNotNull(replyDto);
		assertEquals(replyId, replyDto.getId());
		assertEquals(userId, replyDto.getUserId());
		assertEquals("test_user", replyDto.getUserName());
		assertEquals("This is a reply", replyDto.getContent());
		assertEquals(createdAt, replyDto.getCreatedAt());
		assertEquals(10L, replyDto.getLikesCount());
		assertEquals(likes, replyDto.getLikes());
	}

	@Test
	void testNullValues() {
		ReplyDto replyDto = new ReplyDto();

		assertNull(replyDto.getId());
		assertNull(replyDto.getUserId());
		assertNull(replyDto.getUserName());
		assertNull(replyDto.getContent());
		assertNull(replyDto.getCreatedAt());
		assertNull(replyDto.getLikesCount());
		assertNull(replyDto.getLikes());
	}
}