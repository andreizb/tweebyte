package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        CustomUserDetails userDetails = new CustomUserDetails(userId, "testUser", "testPassword", Collections.EMPTY_LIST);

        assertEquals(userId, userDetails.getUserId());
    }

}