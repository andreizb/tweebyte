package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserLoginRequestTest {

    @Test
    void getEmail() {
        String email = "test@example.com";
        UserLoginRequest userLoginRequest = new UserLoginRequest();
        userLoginRequest.setEmail(email);
        assertEquals(email, userLoginRequest.getEmail());
    }

    @Test
    void getPassword() {
        String password = "testPassword";
        UserLoginRequest userLoginRequest = new UserLoginRequest();
        userLoginRequest.setPassword(password);
        assertEquals(password, userLoginRequest.getPassword());
    }

    @Test
    void setEmail() {
        String email = "test@example.com";
        UserLoginRequest userLoginRequest = new UserLoginRequest();
        userLoginRequest.setEmail(email);
        assertEquals(email, userLoginRequest.getEmail());
    }

    @Test
    void setPassword() {
        String password = "testPassword";
        UserLoginRequest userLoginRequest = new UserLoginRequest();
        userLoginRequest.setPassword(password);
        assertEquals(password, userLoginRequest.getPassword());
    }

}