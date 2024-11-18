package ro.tweebyte.tweetservice.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
@RequiredArgsConstructor
public class TweetException extends RuntimeException {

    public TweetException(Exception cause) {
        super(cause);
    }

    public TweetException(String message) {
        super(message);
    }

}
