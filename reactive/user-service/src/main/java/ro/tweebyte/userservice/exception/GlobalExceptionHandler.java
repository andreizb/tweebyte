package ro.tweebyte.userservice.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Returns the same response shape as the async/user-service handler so the FE Cucumber suite
 * sees the same response shape on both stacks.
 *
 * Response body:
 *     { "timestamp": "...", "status": <int>, "path": "<path>", "errors": ["..."] }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex,
                                                                       ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.BAD_REQUEST),
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex,
                                                                  ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex,
                                                                    ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.UNAUTHORIZED),
                new HttpHeaders(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex,
                                                               ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.TOO_MANY_REQUESTS),
                new HttpHeaders(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(WebExchangeBindException ex,
                                                                      ServerWebExchange exchange) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ResponseEntity<>(getErrorsMap(errors, exchange, HttpStatus.BAD_REQUEST),
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<Map<String, Object>> handleUser(UserException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(FollowRetrievingException.class)
    public ResponseEntity<Map<String, Object>> handleFollowRetrieving(FollowRetrievingException ex,
                                                                      ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, Object> getErrorsMap(List<String> errors, ServerWebExchange exchange, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", ZonedDateTime.now().toString());
        body.put("status", status.value());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("errors", errors);
        return body;
    }
}
