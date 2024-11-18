package ro.tweebyte.userservice.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class UserMapperImplTest {

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @InjectMocks
    private UserMapperImpl userMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testMapToProfileDtoWithValidData() {
        UUID userId = UUID.randomUUID();
        UserEntity entity = UserEntity.builder()
                .id(userId)
                .userName("testuser")
                .email("test@example.com")
                .biography("Bio")
                .isPrivate(false)
                .birthDate(LocalDate.of(2000, 1, 1))
                .createdAt(LocalDateTime.of(2020, 1, 1, 0, 0, 0))
                .build();
        List<TweetDto> tweets = new ArrayList<>();
        tweets.add(new TweetDto());

        UserDto userDto = userMapper.mapToProfileDto(entity, 100L, 50L, tweets);

        assertNotNull(userDto);
        assertEquals(userId, userDto.getId());
        assertEquals("testuser", userDto.getUserName());
        assertEquals(100L, userDto.getFollowing());
        assertEquals(50L, userDto.getFollowers());
        assertEquals(1, userDto.getTweets().size());
    }

    @Test
    void testMapToSummaryDtoWithValidData() {
        UUID userId = UUID.randomUUID();
        UserEntity entity = UserEntity.builder()
                .id(userId)
                .userName("testuser")
                .isPrivate(true)
                .createdAt(LocalDateTime.of(2020, 1, 1, 0, 0, 0))
                .build();

        UserDto userDto = userMapper.mapToSummaryDto(entity);

        assertNotNull(userDto);
        assertEquals(userId, userDto.getId());
        assertEquals("testuser", userDto.getUserName());
        assertTrue(userDto.getIsPrivate());
    }

    @Test
    void testMapToSummaryDtoWithNullEntity() {
        UserDto userDto = userMapper.mapToSummaryDto(null);
        assertNull(userDto);
    }

    @Test
    void testMapRequestToEntity() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUserName("updateduser");
        request.setEmail("updated@example.com");
        request.setIsPrivate(true);

        UserEntity entity = new UserEntity();
        entity.setUserName("originaluser");
        entity.setEmail("original@example.com");

        userMapper.mapRequestToEntity(request, entity);

        assertEquals("updateduser", entity.getUserName());
        assertEquals("updated@example.com", entity.getEmail());
        assertTrue(entity.getIsPrivate());
    }

    @Test
    void testMapRegisterRequestToUserEntityWithValidData() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserName("newuser");
        request.setEmail("new@example.com");
        request.setBiography("New Bio");
        request.setIsPrivate(false);
        request.setBirthDate(LocalDate.of(2000, 1, 1));

        UserEntity entity = userMapper.mapRegisterRequestToUserEntity(request);

        assertNotNull(entity);
        assertEquals("newuser", entity.getUserName());
        assertEquals("new@example.com", entity.getEmail());
        assertEquals("New Bio", entity.getBiography());
        assertFalse(entity.getIsPrivate());
    }

    @Test
    void testMapRegisterRequestToUserEntityWithNullRequest() {
        UserEntity entity = userMapper.mapRegisterRequestToUserEntity(null);
        assertNull(entity);
    }

    @Test
    void testMapRequestToEntitySuper() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setPassword("password123");

        when(bCryptPasswordEncoder.encode(request.getPassword())).thenReturn("password123");
        UserEntity result = userMapper.mapRequestToEntity(request);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
        assertEquals("password123", result.getPassword());
    }

}