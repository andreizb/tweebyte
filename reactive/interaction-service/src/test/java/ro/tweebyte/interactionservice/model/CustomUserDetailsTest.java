package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomUserDetailsTest {

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        CustomUserDetails userDetails = new CustomUserDetails(userId, "pwd");

        assertEquals(userId, userDetails.getUserId());
    }

}