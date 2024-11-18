package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserDtoTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());  // Support for LocalDateTime
	}

	@Test
	void testSerialization() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = UserDto.builder()
			.id(id)
			.userName("test_user")
			.isPrivate(true)
			.createdAt(createdAt)
			.build();

		String json = objectMapper.writeValueAsString(userDto);

		assertTrue(json.contains("\"id\":\"" + id + "\""));
		assertTrue(json.contains("\"user_name\":\"test_user\""));
		assertTrue(json.contains("\"is_private\":true"));
		assertTrue(json.contains("\"created_at\":" + objectMapper.writeValueAsString(createdAt)));
	}

	@Test
	void testDeserialization() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String json = String.format("{\"id\":\"%s\",\"user_name\":\"test_user\",\"is_private\":true,\"created_at\":\"%s\"}",
			id, createdAt);

		UserDto userDto = objectMapper.readValue(json, UserDto.class);

		assertEquals(id, userDto.getId());
		assertEquals("test_user", userDto.getUserName());
		assertTrue(userDto.getIsPrivate());
		assertEquals(createdAt, userDto.getCreatedAt());
	}

	@Test
	void testLombokGeneratedMethods() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto1 = UserDto.builder()
			.id(id)
			.userName("test_user")
			.isPrivate(true)
			.createdAt(createdAt)
			.build();

		UserDto userDto2 = UserDto.builder()
			.id(id)
			.userName("test_user")
			.isPrivate(true)
			.createdAt(createdAt)
			.build();

		assertEquals(userDto1, userDto2);
		assertEquals(userDto1.hashCode(), userDto2.hashCode());
		assertNotNull(userDto1.toString());
		assertTrue(userDto1.toString().contains("UserDto"));

		assertEquals(id, userDto1.getId());
		assertEquals("test_user", userDto1.getUserName());
		assertTrue(userDto1.getIsPrivate());
		assertEquals(createdAt, userDto1.getCreatedAt());
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		UserDto userDto = UserDto.builder()
			.id(id)
			.userName("test_user")
			.isPrivate(false)
			.createdAt(createdAt)
			.build();

		assertNotNull(userDto);
		assertEquals(id, userDto.getId());
		assertEquals("test_user", userDto.getUserName());
		assertFalse(userDto.getIsPrivate());
		assertEquals(createdAt, userDto.getCreatedAt());
	}

	@Test
	void testNullValues() {
		UserDto userDto = new UserDto();

		assertNull(userDto.getId());
		assertNull(userDto.getUserName());
		assertNull(userDto.getIsPrivate());
		assertNull(userDto.getCreatedAt());
	}
}