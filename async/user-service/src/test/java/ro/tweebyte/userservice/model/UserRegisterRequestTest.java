package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class UserRegisterRequestTest {

    @Test
    void getUserName() {
        String userName = "testUser";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setUserName(userName);
        assertEquals(userName, userRegisterRequest.getUserName());
    }

    @Test
    void getEmail() {
        String email = "test@example.com";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setEmail(email);
        assertEquals(email, userRegisterRequest.getEmail());
    }

    @Test
    void getBiography() {
        String biography = "Test biography";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setBiography(biography);
        assertEquals(biography, userRegisterRequest.getBiography());
    }

    @Test
    void getIsPrivate() {
        Boolean isPrivate = true;
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setIsPrivate(isPrivate);
        assertEquals(isPrivate, userRegisterRequest.getIsPrivate());
    }

    @Test
    void getPassword() {
        String password = "testPassword";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setPassword(password);
        assertEquals(password, userRegisterRequest.getPassword());
    }

    @Test
    void getBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setBirthDate(birthDate);
        assertEquals(birthDate, userRegisterRequest.getBirthDate());
    }

    @Test
    void setUserName() {
        String userName = "testUser";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setUserName(userName);
        assertEquals(userName, userRegisterRequest.getUserName());
    }

    @Test
    void setEmail() {
        String email = "test@example.com";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setEmail(email);
        assertEquals(email, userRegisterRequest.getEmail());
    }

    @Test
    void setBiography() {
        String biography = "Test biography";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setBiography(biography);
        assertEquals(biography, userRegisterRequest.getBiography());
    }

    @Test
    void setIsPrivate() {
        Boolean isPrivate = true;
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setIsPrivate(isPrivate);
        assertEquals(isPrivate, userRegisterRequest.getIsPrivate());
    }

    @Test
    void setPassword() {
        String password = "testPassword";
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setPassword(password);
        assertEquals(password, userRegisterRequest.getPassword());
    }

    @Test
    void setBirthDate() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
        userRegisterRequest.setBirthDate(birthDate);
        assertEquals(birthDate, userRegisterRequest.getBirthDate());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserRegisterRequest request = new UserRegisterRequest(
                "testUser",
                "test@example.com",
                "This is a biography",
                true,
                "securePassword123",
                birthDate
        );

        assertEquals("testUser", request.getUserName());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("This is a biography", request.getBiography());
        assertTrue(request.getIsPrivate());
        assertEquals("securePassword123", request.getPassword());
        assertEquals(birthDate, request.getBirthDate());
    }

    @Test
    void testBuilder() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        UserRegisterRequest request = UserRegisterRequest.builder()
                .userName("testUser")
                .email("test@example.com")
                .biography("This is a biography")
                .isPrivate(true)
                .password("securePassword123")
                .birthDate(birthDate)
                .build();

        assertEquals("testUser", request.getUserName());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("This is a biography", request.getBiography());
        assertTrue(request.getIsPrivate());
        assertEquals("securePassword123", request.getPassword());
        assertEquals(birthDate, request.getBirthDate());
    }

}