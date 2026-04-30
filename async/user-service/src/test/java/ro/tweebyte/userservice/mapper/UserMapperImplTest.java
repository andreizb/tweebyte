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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Exercises every branch in the MapStruct-generated UserMapperImpl. The Impl
 * is excluded from JaCoCo line/branch coverage targets, but adding these tests
 * still validates behaviour and contributes to the per-stack test-count parity.
 * Mirrors the reactive {@code mapper.UserMapperTest} catalog where applicable.
 */
class UserMapperImplTest {

    private UserMapperImpl mapper;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        mapper = new UserMapperImpl();
        encoder = mock(BCryptPasswordEncoder.class);
        ReflectionTestUtils.setField(mapper, "encoder", encoder);
    }

    @Test
    void mapToProfileDtoAllArgsNullReturnsNull() {
        assertNull(mapper.mapToProfileDto(null, null, null, null));
    }

    @Test
    void mapToProfileDtoCopiesEntityAndCounters() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserName("alice");
        entity.setEmail("a@b");
        entity.setBiography("bio");
        entity.setIsPrivate(true);
        entity.setBirthDate(LocalDate.of(1990, 1, 1));
        entity.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));

        UserDto dto = mapper.mapToProfileDto(entity, 5L, 7L, List.of(new TweetDto()));
        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals("alice", dto.getUserName());
        assertEquals("a@b", dto.getEmail());
        assertEquals("bio", dto.getBiography());
        assertTrue(dto.getIsPrivate());
        assertEquals(5L, dto.getFollowing());
        assertEquals(7L, dto.getFollowers());
        assertEquals(1, dto.getTweets().size());
    }

    @Test
    void mapToProfileDtoNullEntityWithCountersStillBuildsDto() {
        UserDto dto = mapper.mapToProfileDto(null, 1L, 2L, Collections.emptyList());
        assertNotNull(dto);
        assertEquals(1L, dto.getFollowing());
        assertEquals(2L, dto.getFollowers());
        assertNotNull(dto.getTweets());
    }

    @Test
    void mapToProfileDtoNullTweetsLeavesTweetsNull() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        UserDto dto = mapper.mapToProfileDto(entity, 0L, 0L, null);
        assertNotNull(dto);
        assertNull(dto.getTweets());
    }

    @Test
    void mapToSummaryDtoNullEntityReturnsNull() {
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
        assertNotNull(dto);
        assertEquals("bob", dto.getUserName());
        assertFalse(dto.getIsPrivate());
        assertEquals(entity.getCreatedAt(), dto.getCreatedAt());
        // summary should not include profile-only fields
        assertNull(dto.getEmail());
        assertNull(dto.getBiography());
    }

    @Test
    void mapRequestToEntityUpdateNullRequestNoOp() {
        UserEntity entity = new UserEntity();
        entity.setUserName("alice");
        mapper.mapRequestToEntity((UserUpdateRequest) null, entity);
        assertEquals("alice", entity.getUserName());
    }

    @Test
    void mapRequestToEntityUpdateAllNullsLeavesEntity() {
        UserEntity entity = new UserEntity();
        entity.setUserName("alice");
        entity.setEmail("a@b");
        UserUpdateRequest req = new UserUpdateRequest();  // all nulls
        mapper.mapRequestToEntity(req, entity);
        assertEquals("alice", entity.getUserName());
        assertEquals("a@b", entity.getEmail());
    }

    @Test
    void mapRequestToEntityUpdateAllPresentOverwrites() {
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

        // mapRequestToEntity now bcrypt-encodes the password BEFORE
        // delegating to copyRequestFieldsToEntity. Stub the mock encoder so we
        // can verify the encoded value lands on the entity.
        org.mockito.Mockito.when(encoder.encode("newpw")).thenReturn("$bcrypt$newpw");

        mapper.mapRequestToEntity(req, entity);
        assertEquals("new", entity.getUserName());
        assertEquals("new@x", entity.getEmail());
        assertEquals("newbio", entity.getBiography());
        assertEquals("$bcrypt$newpw", entity.getPassword());
        org.mockito.Mockito.verify(encoder).encode("newpw");
        assertEquals(LocalDate.of(2000, 2, 2), entity.getBirthDate());
        assertTrue(entity.getIsPrivate());
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
    void mapRegisterRequestToUserEntityNullReturnsNull() {
        // mapRegisterRequestToUserEntity is the protected MapStruct hook; on null input
        // the generated code short-circuits to null. The public wrapper does NOT guard
        // against null, so test the protected variant directly via reflection.
        java.lang.reflect.Method m;
        try {
            m = UserMapperImpl.class.getDeclaredMethod("mapRegisterRequestToUserEntity", UserRegisterRequest.class);
            m.setAccessible(true);
            Object result = m.invoke(mapper, (Object) null);
            assertNull(result);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void mapRequestToEntityRegisterPopulatesAllFields() {
        given(encoder.encode(anyString())).willReturn("encpw");
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
        assertNotNull(entity.getId());
        assertNotNull(entity.getCreatedAt());
    }
}
