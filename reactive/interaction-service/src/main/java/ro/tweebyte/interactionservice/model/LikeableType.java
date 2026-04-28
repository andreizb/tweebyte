package ro.tweebyte.interactionservice.model;

import java.util.Arrays;

public enum LikeableType {
    TWEET, REPLY;

    public LikeableType fromString(String likeableType) {
        return Arrays.stream(values()).filter(e -> e.name().equals(likeableType)).findFirst().orElseThrow(RuntimeException::new);
    }
}
