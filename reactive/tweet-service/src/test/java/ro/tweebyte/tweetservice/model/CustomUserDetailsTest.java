package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomUserDetailsTest {

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        CustomUserDetails userDetails = new CustomUserDetails(userId, "testUser@test.com");

        assertEquals(userId, userDetails.getUserId());
    }

    @Test
    void getEmail() {
        String email = "testUser@test.com";
        CustomUserDetails userDetails = new CustomUserDetails(UUID.randomUUID(), "testUser@test.com");

        assertEquals(email, userDetails.getEmail());
    }

}