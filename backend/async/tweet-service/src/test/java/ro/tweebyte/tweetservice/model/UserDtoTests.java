/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoTests {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		this.objectMapper = new ObjectMapper();
		this.objectMapper.registerModule(new JavaTimeModule()); // Support for
																// LocalDateTime
		// UserDto.createdAt carries @JsonFormat(shape = STRING), so it serializes
		// as an ISO-8601 string; disable timestamp arrays here so the standalone
		// createdAt serialization used in the assertion matches that contract.
		this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	@Test
	void testSerialization() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = UserDto.builder().id(id).userName("test_user").isPrivate(true).createdAt(createdAt).build();

		String json = this.objectMapper.writeValueAsString(userDto);

		assertThat(json).contains("\"id\":\"" + id + "\"", "\"user_name\":\"test_user\"", "\"is_private\":true",
				"\"created_at\":" + this.objectMapper.writeValueAsString(createdAt));
	}

	@Test
	void testDeserialization() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String json = String.format(
				"{\"id\":\"%s\",\"user_name\":\"test_user\",\"is_private\":true,\"created_at\":\"%s\"}", id, createdAt);

		UserDto userDto = this.objectMapper.readValue(json, UserDto.class);

		assertThat(userDto.getId()).isEqualTo(id);
		assertThat(userDto.getUserName()).isEqualTo("test_user");
		assertThat(userDto.getIsPrivate()).isTrue();
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testLombokGeneratedMethods() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto1 = UserDto.builder().id(id).userName("test_user").isPrivate(true).createdAt(createdAt).build();

		UserDto userDto2 = UserDto.builder().id(id).userName("test_user").isPrivate(true).createdAt(createdAt).build();

		assertThat(userDto2).isEqualTo(userDto1).hasSameHashCodeAs(userDto1);
		assertThat(userDto1.toString()).isNotNull();
		assertThat(userDto1.toString()).contains("UserDto");

		assertThat(userDto1.getId()).isEqualTo(id);
		assertThat(userDto1.getUserName()).isEqualTo("test_user");
		assertThat(userDto1.getIsPrivate()).isTrue();
		assertThat(userDto1.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		UserDto userDto = UserDto.builder().id(id).userName("test_user").isPrivate(false).createdAt(createdAt).build();

		assertThat(userDto).isNotNull();
		assertThat(userDto.getId()).isEqualTo(id);
		assertThat(userDto.getUserName()).isEqualTo("test_user");
		assertThat(userDto.getIsPrivate()).isFalse();
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testNullValues() {
		UserDto userDto = new UserDto();

		assertThat(userDto.getId()).isNull();
		assertThat(userDto.getUserName()).isNull();
		assertThat(userDto.getIsPrivate()).isNull();
		assertThat(userDto.getCreatedAt()).isNull();
	}

}
