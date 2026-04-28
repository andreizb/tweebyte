package ro.tweebyte.interactionservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class TweetNotFoundException extends RuntimeException {

    public TweetNotFoundException(String message) {
        super(message);
    }

}
