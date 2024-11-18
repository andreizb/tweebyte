package ro.tweebyte.userservice.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
@RequiredArgsConstructor
public class UserException extends RuntimeException {

    public UserException(Exception cause) {
        super(cause);
    }

    public UserException(String message) {
        super(message);
    }

}