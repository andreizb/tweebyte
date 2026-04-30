package ro.tweebyte.userservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch-coverage extras for UserEntity: builder/all-args/no-args/toString,
 * mirroring the per-class file naming convention used elsewhere
 * (e.g. *BranchTest.java).
 */
class UserEntityBranchTest {

    @Test
    void allArgsConstructorPopulatesAllFields() {
        UUID id = UUID.randomUUID();
        LocalDate birth = LocalDate.of(1990, 5, 5);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 0, 0);
        UserEntity entity = new UserEntity(id, "alice", "a@b", "bio", "pw", true, birth, createdAt);
        assertEquals(id, entity.getId());
        assertEquals("alice", entity.getUserName());
        assertEquals("a@b", entity.getEmail());
        assertEquals("bio", entity.getBiography());
        assertEquals("pw", entity.getPassword());
        assertTrue(entity.getIsPrivate());
        assertEquals(birth, entity.getBirthDate());
        assertEquals(createdAt, entity.getCreatedAt());
    }

    @Test
    void noArgsConstructorYieldsNullFields() {
        UserEntity entity = new UserEntity();
        assertNull(entity.getId());
        assertNull(entity.getUserName());
        assertNull(entity.getEmail());
        assertNull(entity.getBiography());
        assertNull(entity.getPassword());
        assertNull(entity.getIsPrivate());
        assertNull(entity.getBirthDate());
        assertNull(entity.getCreatedAt());
    }

    @Test
    void builderPopulatesAllFields() {
        UUID id = UUID.randomUUID();
        LocalDate birth = LocalDate.of(2000, 2, 2);
        LocalDateTime createdAt = LocalDateTime.now();
        UserEntity entity = UserEntity.builder()
                .id(id)
                .userName("bob")
                .email("b@x")
                .biography("b")
                .password("h")
                .isPrivate(false)
                .birthDate(birth)
                .createdAt(createdAt)
                .build();
        assertEquals(id, entity.getId());
        assertEquals("bob", entity.getUserName());
        assertEquals("b@x", entity.getEmail());
        assertEquals("b", entity.getBiography());
        assertEquals("h", entity.getPassword());
        assertEquals(false, entity.getIsPrivate());
        assertEquals(birth, entity.getBirthDate());
        assertEquals(createdAt, entity.getCreatedAt());
    }

    @Test
    void builderInstanceReturnsBuilderType() {
        // Cover the static builder() factory method
        assertNotNull(UserEntity.builder());
    }

    @Test
    void isPrivateFalseRoundTrips() {
        UserEntity entity = UserEntity.builder().isPrivate(false).build();
        assertEquals(false, entity.getIsPrivate());
    }

    @Test
    void differentInstancesAreNotIdentityEqual() {
        UserEntity a = new UserEntity();
        UserEntity b = new UserEntity();
        assertNotEquals(System.identityHashCode(a), System.identityHashCode(b));
    }

    @Test
    void settersChainOnSameInstance() {
        UserEntity entity = new UserEntity();
        entity.setUserName("x");
        entity.setEmail("y");
        assertEquals("x", entity.getUserName());
        assertEquals("y", entity.getEmail());
    }

    @Test
    void overwriteFieldKeepsLatestValue() {
        UserEntity entity = new UserEntity();
        entity.setUserName("first");
        entity.setUserName("second");
        assertEquals("second", entity.getUserName());
    }
}
