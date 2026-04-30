package ro.tweebyte.userservice.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercises every branch in UserMapper. The mapper is a hand-written component
 * (not MapStruct-generated), so it's not in the JaCoCo excludes — every null-guard
 * counts toward branch coverage.
 */
class UserMapperTest {

    private UserMapper mapper;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        mapper = new UserMapper();
        encoder = mock(BCryptPasswordEncoder.class);
        ReflectionTestUtils.setField(mapper, "passwordEncoder", encoder);
    }

    // --- mapToProfileDto ----------------------------------------------

    @Test
    void mapToProfileDtoReturnsNullWhenEntityNull() {
        assertNull(mapper.mapToProfileDto(null, 1L, 2L, Collections.emptyList()));
    }

    @Test
    void mapToProfileDtoCopiesAllFieldsAndTweets() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserName("alice");
        entity.setEmail("a@b");
        entity.setBiography("bio");
        entity.setIsPrivate(true);
        entity.setBirthDate(LocalDate.of(1990, 1, 2));
        entity.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));

        TweetDto tweet = new TweetDto();
        tweet.setId(UUID.randomUUID());
        UserDto dto = mapper.mapToProfileDto(entity, 5L, 7L, List.of(tweet));

        assertEquals(entity.getId(), dto.getId());
        assertEquals("alice", dto.getUserName());
        assertEquals("a@b", dto.getEmail());
        assertEquals("bio", dto.getBiography());
        assertTrue(dto.getIsPrivate());
        assertEquals(LocalDate.of(1990, 1, 2), dto.getBirthDate());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), dto.getCreatedAt());
        assertEquals(5L, dto.getFollowing());
        assertEquals(7L, dto.getFollowers());
        assertEquals(1, dto.getTweets().size());
    }

    @Test
    void mapToProfileDtoNullTweetsYieldsEmptyList() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());

        UserDto dto = mapper.mapToProfileDto(entity, 0L, 0L, null);

        assertNotNull(dto.getTweets());
        assertTrue(dto.getTweets().isEmpty());
    }

    // --- mapToSummaryDto ----------------------------------------------

    @Test
    void mapToSummaryDtoReturnsNullWhenEntityNull() {
        assertNull(mapper.mapToSummaryDto(null));
    }

    @Test
    void mapToSummaryDtoCopiesSummaryFields() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserName("bob");
        entity.setIsPrivate(false);
        entity.setCreatedAt(LocalDateTime.of(2024, 5, 5, 5, 5));

        UserDto dto = mapper.mapToSummaryDto(entity);

        assertEquals(entity.getId(), dto.getId());
        assertEquals("bob", dto.getUserName());
        assertFalse(dto.getIsPrivate());
        assertEquals(entity.getCreatedAt(), dto.getCreatedAt());
        // summary should not include profile-only fields
        assertNull(dto.getEmail());
        assertNull(dto.getBiography());
    }

    // --- mapRequestToEntity (UserUpdateRequest variant) ---------------

    @Test
    void mapRequestToEntityUpdateNoOpWhenRequestNull() {
        UserEntity entity = new UserEntity();
        entity.setUserName("alice");
        mapper.mapRequestToEntity((UserUpdateRequest) null, entity);
        assertEquals("alice", entity.getUserName());
    }

    @Test
    void mapRequestToEntityUpdateNoOpWhenEntityNull() {
        // Should simply return without throwing.
        mapper.mapRequestToEntity(new UserUpdateRequest(), null);
    }

    @Test
    void mapRequestToEntityUpdateAllNullsLeavesEntityIntact() {
        UserEntity entity = new UserEntity();
        entity.setUserName("alice");
        entity.setEmail("a@b");
        entity.setBiography("bio");
        entity.setPassword("oldhash");
        entity.setBirthDate(LocalDate.of(1990, 1, 1));
        entity.setIsPrivate(false);

        UserUpdateRequest req = new UserUpdateRequest();  // all nulls

        mapper.mapRequestToEntity(req, entity);

        assertEquals("alice", entity.getUserName());
        assertEquals("a@b", entity.getEmail());
        assertEquals("bio", entity.getBiography());
        assertEquals("oldhash", entity.getPassword());
        assertEquals(LocalDate.of(1990, 1, 1), entity.getBirthDate());
        assertFalse(entity.getIsPrivate());
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void mapRequestToEntityUpdateAllPresentOverwritesAndEncodesPassword() {
        given(encoder.encode("newpw")).willReturn("encNewpw");

        UserEntity entity = new UserEntity();
        entity.setUserName("old");
        entity.setEmail("old@x");
        entity.setBiography("oldbio");
        entity.setPassword("oldhash");
        entity.setBirthDate(LocalDate.of(1990, 1, 1));
        entity.setIsPrivate(false);

        UserUpdateRequest req = new UserUpdateRequest();
        req.setUserName("new");
        req.setEmail("new@x");
        req.setBiography("newbio");
        req.setPassword("newpw");
        req.setBirthDate(LocalDate.of(2000, 2, 2));
        req.setIsPrivate(true);

        mapper.mapRequestToEntity(req, entity);

        assertEquals("new", entity.getUserName());
        assertEquals("new@x", entity.getEmail());
        assertEquals("newbio", entity.getBiography());
        assertEquals("encNewpw", entity.getPassword());
        assertEquals(LocalDate.of(2000, 2, 2), entity.getBirthDate());
        assertTrue(entity.getIsPrivate());
        verify(encoder).encode("newpw");
    }

    @Test
    void mapRequestToEntityUpdateOnlyUserName() {
        UserEntity entity = new UserEntity();
        entity.setUserName("old");
        entity.setEmail("keep@x");
        UserUpdateRequest req = new UserUpdateRequest();
        req.setUserName("new");
        mapper.mapRequestToEntity(req, entity);
        assertEquals("new", entity.getUserName());
        assertEquals("keep@x", entity.getEmail());
    }

    @Test
    void mapRequestToEntityUpdateOnlyEmail() {
        UserEntity entity = new UserEntity();
        entity.setEmail("old@x");
        UserUpdateRequest req = new UserUpdateRequest();
        req.setEmail("new@x");
        mapper.mapRequestToEntity(req, entity);
        assertEquals("new@x", entity.getEmail());
    }

    @Test
    void mapRequestToEntityUpdateOnlyBiography() {
        UserEntity entity = new UserEntity();
        entity.setBiography("old");
        UserUpdateRequest req = new UserUpdateRequest();
        req.setBiography("new");
        mapper.mapRequestToEntity(req, entity);
        assertEquals("new", entity.getBiography());
    }

    @Test
    void mapRequestToEntityUpdateOnlyPasswordIsEncoded() {
        given(encoder.encode("pw")).willReturn("hash");
        UserEntity entity = new UserEntity();
        entity.setPassword("oldhash");
        UserUpdateRequest req = new UserUpdateRequest();
        req.setPassword("pw");
        mapper.mapRequestToEntity(req, entity);
        assertEquals("hash", entity.getPassword());
    }

    @Test
    void mapRequestToEntityUpdateOnlyBirthDate() {
        UserEntity entity = new UserEntity();
        entity.setBirthDate(LocalDate.of(1990, 1, 1));
        UserUpdateRequest req = new UserUpdateRequest();
        req.setBirthDate(LocalDate.of(2000, 1, 1));
        mapper.mapRequestToEntity(req, entity);
        assertEquals(LocalDate.of(2000, 1, 1), entity.getBirthDate());
    }

    @Test
    void mapRequestToEntityUpdateOnlyIsPrivate() {
        UserEntity entity = new UserEntity();
        entity.setIsPrivate(false);
        UserUpdateRequest req = new UserUpdateRequest();
        req.setIsPrivate(true);
        mapper.mapRequestToEntity(req, entity);
        assertTrue(entity.getIsPrivate());
    }

    // --- mapRequestToEntity (UserRegisterRequest variant) -------------

    @Test
    void mapRequestToEntityRegisterReturnsNullWhenRequestNull() {
        assertNull(mapper.mapRequestToEntity((UserRegisterRequest) null));
    }

    @Test
    void mapRequestToEntityRegisterPopulatesAllFields() {
        given(encoder.encode("pw")).willReturn("encpw");
        UserRegisterRequest req = UserRegisterRequest.builder()
                .userName("alice")
                .email("a@b")
                .biography("bio")
                .password("pw")
                .birthDate(LocalDate.of(1990, 1, 2))
                .isPrivate(true)
                .build();

        UserEntity entity = mapper.mapRequestToEntity(req);

        assertNotNull(entity);
        assertEquals("alice", entity.getUserName());
        assertEquals("a@b", entity.getEmail());
        assertEquals("bio", entity.getBiography());
        assertEquals("encpw", entity.getPassword());
        assertEquals(LocalDate.of(1990, 1, 2), entity.getBirthDate());
        assertTrue(entity.getIsPrivate());
        assertTrue(entity.isInsertable());
        assertTrue(entity.isNew());
        assertNotNull(entity.getId());
        assertNotNull(entity.getCreatedAt());
    }

    @Test
    void mapRequestToEntityRegisterDefaultsIsPrivateToFalseWhenNull() {
        // when request.getIsPrivate() == null we use Boolean.FALSE
        given(encoder.encode("pw")).willReturn("encpw");
        UserRegisterRequest req = UserRegisterRequest.builder()
                .userName("bob")
                .email("b@b")
                .password("pw")
                .birthDate(LocalDate.of(1991, 1, 1))
                .isPrivate(null)
                .build();

        UserEntity entity = mapper.mapRequestToEntity(req);

        assertNotNull(entity);
        assertFalse(entity.getIsPrivate());
    }

    @Test
    void mapRequestToEntityRegisterIsPrivateFalseExplicit() {
        given(encoder.encode("pw")).willReturn("encpw");
        UserRegisterRequest req = UserRegisterRequest.builder()
                .userName("bob")
                .email("b@b")
                .password("pw")
                .birthDate(LocalDate.of(1991, 1, 1))
                .isPrivate(false)
                .build();

        UserEntity entity = mapper.mapRequestToEntity(req);
        assertFalse(entity.getIsPrivate());
    }
}
