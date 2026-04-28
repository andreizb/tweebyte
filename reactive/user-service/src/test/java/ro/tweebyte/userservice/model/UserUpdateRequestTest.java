package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserUpdateRequestTest {

    @Test
    void getUserName() {
        String userName = "testUser";
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setUserName(userName);
        assertEquals(userName, userEntity.getUserName());
    }

    @Test
    void getEmail() {
        String email = "test@example.com";
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setEmail(email);
        assertEquals(email, userEntity.getEmail());
    }

    @Test
    void getPassword() {
        String password = "testPassword";
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setPassword(password);
        assertEquals(password, userEntity.getPassword());
    }

    @Test
    void getIsPrivate() {
        Boolean isPrivate = true;
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setIsPrivate(isPrivate);
        assertEquals(isPrivate, userEntity.getIsPrivate());
    }

    @Test
    void getBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setBirthDate(birthDate);
        assertEquals(birthDate, userEntity.getBirthDate());
    }


    @Test
    void setUserName() {
        String userName = "testUser";
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setUserName(userName);
        assertEquals(userName, userEntity.getUserName());
    }

    @Test
    void setEmail() {
        String email = "test@example.com";
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setEmail(email);
        assertEquals(email, userEntity.getEmail());
    }


    @Test
    void setPassword() {
        String password = "testPassword";
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setPassword(password);
        assertEquals(password, userEntity.getPassword());
    }

    @Test
    void setIsPrivate() {
        Boolean isPrivate = true;
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setIsPrivate(isPrivate);
        assertEquals(isPrivate, userEntity.getIsPrivate());
    }

    @Test
    void setBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserUpdateRequest userEntity = new UserUpdateRequest();
        userEntity.setBirthDate(birthDate);
        assertEquals(birthDate, userEntity.getBirthDate());
    }

}