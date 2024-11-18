package ro.tweebyte.userservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserEntityTest {

    @Test
    void getId() {
        UserEntity userEntity = new UserEntity();
        UUID id = UUID.randomUUID();
        userEntity.setId(id);

        assertEquals(id, userEntity.getId());
    }

    @Test
    void getUserName() {
        UserEntity userEntity = new UserEntity();
        String userName = "testUser";
        userEntity.setUserName(userName);

        assertEquals(userName, userEntity.getUserName());
    }

    @Test
    void getEmail() {
        UserEntity userEntity = new UserEntity();
        String email = "test@example.com";
        userEntity.setEmail(email);

        assertEquals(email, userEntity.getEmail());
    }

    @Test
    void getBiography() {
        UserEntity userEntity = new UserEntity();
        String biography = "Test biography";
        userEntity.setBiography(biography);

        assertEquals(biography, userEntity.getBiography());
    }

    @Test
    void getPassword() {
        UserEntity userEntity = new UserEntity();
        String password = "testPassword";
        userEntity.setPassword(password);

        assertEquals(password, userEntity.getPassword());
    }

    @Test
    void getIsPrivate() {
        UserEntity userEntity = new UserEntity();
        Boolean isPrivate = true;
        userEntity.setIsPrivate(isPrivate);

        assertTrue(userEntity.getIsPrivate());
    }

    @Test
    void getBirthDate() {
        UserEntity userEntity = new UserEntity();
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        userEntity.setBirthDate(birthDate);

        assertEquals(birthDate, userEntity.getBirthDate());
    }

    @Test
    void getCreatedAt() {
        UserEntity userEntity = new UserEntity();
        LocalDateTime createdAt = LocalDateTime.now();
        userEntity.setCreatedAt(createdAt);

        assertEquals(createdAt, userEntity.getCreatedAt());
    }

    @Test
    void getIsInsertable() {
        UserEntity userEntity = new UserEntity();
        LocalDateTime createdAt = LocalDateTime.now();
        userEntity.setCreatedAt(createdAt);

        assertEquals(createdAt, userEntity.getCreatedAt());
    }

    @Test
    void setIsInsertable() {
        UserEntity userEntity = new UserEntity();
        Boolean isInsertable = true;
        userEntity.setInsertable(true);

        assertEquals(isInsertable, userEntity.isInsertable());
    }

    @Test
    void testIsNew() {
        UserEntity userEntity = new UserEntity();
        userEntity.setInsertable(true);
        assertTrue(userEntity.isNew());
    }

    @Test
    void setId() {
        UserEntity userEntity = new UserEntity();
        UUID id = UUID.randomUUID();
        userEntity.setId(id);

        assertEquals(id, userEntity.getId());
    }

    @Test
    void setUserName() {
        UserEntity userEntity = new UserEntity();
        String userName = "testUser";
        userEntity.setUserName(userName);

        assertEquals(userName, userEntity.getUserName());
    }

    @Test
    void setEmail() {
        UserEntity userEntity = new UserEntity();
        String email = "test@example.com";
        userEntity.setEmail(email);

        assertEquals(email, userEntity.getEmail());
    }

    @Test
    void setBiography() {
        UserEntity userEntity = new UserEntity();
        String biography = "Test biography";
        userEntity.setBiography(biography);

        assertEquals(biography, userEntity.getBiography());
    }

    @Test
    void setPassword() {
        UserEntity userEntity = new UserEntity();
        String password = "testPassword";
        userEntity.setPassword(password);

        assertEquals(password, userEntity.getPassword());
    }

    @Test
    void setIsPrivate() {
        UserEntity userEntity = new UserEntity();
        Boolean isPrivate = true;
        userEntity.setIsPrivate(isPrivate);

        assertTrue(userEntity.getIsPrivate());
    }

    @Test
    void setBirthDate() {
        UserEntity userEntity = new UserEntity();
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        userEntity.setBirthDate(birthDate);

        assertEquals(birthDate, userEntity.getBirthDate());
    }

    @Test
    void setCreatedAt() {
        UserEntity userEntity = new UserEntity();
        LocalDateTime createdAt = LocalDateTime.now();
        userEntity.setCreatedAt(createdAt);

        assertEquals(createdAt, userEntity.getCreatedAt());
    }

}