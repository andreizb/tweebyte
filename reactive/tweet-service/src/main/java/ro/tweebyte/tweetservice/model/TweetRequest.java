package ro.tweebyte.tweetservice.model;

import java.util.UUID;

public interface TweetRequest {

    UUID getId();

    UUID getUserId();

    String getContent();

}
