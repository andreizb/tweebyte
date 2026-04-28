package ro.tweebyte.interactionservice.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CustomUserDetails {

    private final UUID userId;

    private final String email;

    public CustomUserDetails(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
    }

}
