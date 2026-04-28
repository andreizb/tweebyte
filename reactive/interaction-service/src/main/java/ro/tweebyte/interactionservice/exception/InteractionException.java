package ro.tweebyte.interactionservice.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
@RequiredArgsConstructor
public class InteractionException extends RuntimeException {

    public InteractionException(Throwable cause) {
        super(cause);
    }

    public InteractionException(String message) {
        super(message);
    }

}
