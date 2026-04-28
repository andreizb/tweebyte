package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserDtoTest {

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        String userName = "TestUser";
        Boolean isPrivate = true;
        LocalDateTime createdAt = LocalDateTime.now();

        UserDto userDto = new UserDto();
        userDto.setUserName(userName);
        userDto.setId(id);
        userDto.setIsPrivate(isPrivate);
        userDto.setCreatedAt(createdAt);

        assertEquals(id, userDto.getId());
        assertEquals(userName, userDto.getUserName());
        assertEquals(isPrivate, userDto.getIsPrivate());
        assertEquals(createdAt, userDto.getCreatedAt());

        // Test setters
        UUID newId = UUID.randomUUID();
        String newUserName = "NewUser";
        Boolean newIsPrivate = false;
        LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);

        userDto.setId(newId);
        userDto.setUserName(newUserName);
        userDto.setIsPrivate(newIsPrivate);
        userDto.setCreatedAt(newCreatedAt);

        assertEquals(newId, userDto.getId());
        assertEquals(newUserName, userDto.getUserName());
        assertEquals(newIsPrivate, userDto.getIsPrivate());
        assertEquals(newCreatedAt, userDto.getCreatedAt());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        String userName = "TestUser";
        Boolean isPrivate = true;
        LocalDateTime createdAt = LocalDateTime.now();

        UserDto userDto = new UserDto(id, userName, isPrivate, createdAt);

        assertEquals(id, userDto.getId());
        assertEquals(userName, userDto.getUserName());
        assertEquals(isPrivate, userDto.getIsPrivate());
        assertEquals(createdAt, userDto.getCreatedAt());
    }

    @Test
    void testBuilder() {
        UUID id = UUID.randomUUID();
        String userName = "TestUser";
        Boolean isPrivate = true;
        LocalDateTime createdAt = LocalDateTime.now();

        UserDto userDto = UserDto.builder()
            .id(id)
            .userName(userName)
            .isPrivate(isPrivate)
            .createdAt(createdAt)
            .build();

        assertEquals(id, userDto.getId());
        assertEquals(userName, userDto.getUserName());
        assertEquals(isPrivate, userDto.getIsPrivate());
        assertEquals(createdAt, userDto.getCreatedAt());
    }

    @Test
    void testNoArgsConstructor() {
        UserDto userDto = new UserDto();

        assertNotNull(userDto);
    }

}