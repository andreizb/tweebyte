package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationResponseTest {

    @Test
    void getToken() {
        AuthenticationResponse authenticationResponse = new AuthenticationResponse();
        String token = "testToken";
        authenticationResponse.setToken(token);

        assertEquals(token, authenticationResponse.getToken());
    }

    @Test
    void setToken() {
        AuthenticationResponse authenticationResponse = new AuthenticationResponse();
        String token = "testToken";
        authenticationResponse.setToken(token);

        assertEquals(token, authenticationResponse.getToken());
    }

}