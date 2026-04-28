package ro.tweebyte.interactionservice.model;

import java.util.Arrays;

public enum Status {
    PENDING, ACCEPTED, REJECTED;

    public Status fromString(String status) {
        return Arrays.stream(values()).filter(e -> e.name().equals(status)).findFirst().orElseThrow(RuntimeException::new);
    }
}
