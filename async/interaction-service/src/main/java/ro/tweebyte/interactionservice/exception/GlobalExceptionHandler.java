package ro.tweebyte.interactionservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler for the async interaction-service. Maps service-layer
 * IllegalArgumentException (unauthorised / not-found cases from
 * ReplyService / RetweetService / LikeService) and Spring's
 * MethodArgumentTypeMismatchException (malformed UUID path variables) to
 * structured 4xx responses, matching the user-service / tweet-service
 * error-shape on the same stack.
 *
 * Body shape: { "timestamp": "...", "status": <int>, "path": "<path>", "errors": ["..."] }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ResponseEntity<>(getErrorsMap(errors, request, HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ResponseEntity<>(getErrorsMap(errors, request, HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handlePathTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.BAD_REQUEST),
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTweetNotFound(TweetNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FollowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFollowNotFound(FollowNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InteractionException.class)
    public ResponseEntity<Map<String, Object>> handleInteractionException(InteractionException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleException(Throwable ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, Object> getErrorsMap(List<String> errors, HttpServletRequest request, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", ZonedDateTime.now().toString());
        body.put("status", status.value());
        body.put("path", request.getRequestURI());
        body.put("errors", errors);
        return body;
    }
}
