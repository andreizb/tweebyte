package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void testAllArgsConstructor() {
        AuthenticationResponse response = new AuthenticationResponse("sampleToken");

        assertNotNull(response);
        assertEquals("sampleToken", response.getToken());
    }

    @Test
    void testBuilder() {
        AuthenticationResponse response = AuthenticationResponse.builder()
                .token("sampleToken")
                .build();

        assertNotNull(response);
        assertEquals("sampleToken", response.getToken());
    }

}