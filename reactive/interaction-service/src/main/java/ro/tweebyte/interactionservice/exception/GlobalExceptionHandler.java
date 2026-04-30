package ro.tweebyte.interactionservice.exception;

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
 * GlobalExceptionHandler for the reactive interaction-service. Maps
 * service-layer IllegalArgumentException (unauthorised / not-found cases
 * from ReplyService / RetweetService) to structured 4xx responses, matching
 * the user-service / tweet-service handler pattern on this stack and the
 * async/interaction-service handler shape.
 *
 * Body shape: { "timestamp": "...", "status": <int>, "path": "<path>", "errors": ["..."] }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(WebExchangeBindException ex, ServerWebExchange exchange) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ResponseEntity<>(getErrorsMap(errors, exchange, HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTweetNotFound(TweetNotFoundException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FollowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFollowNotFound(FollowNotFoundException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InteractionException.class)
    public ResponseEntity<Map<String, Object>> handleInteractionException(InteractionException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Mirrors the reactive tweet-service handler: let
     * ResponseStatusException(400, ...) for malformed @PathVariable UUIDs
     * surface as 400 instead of being clobbered by the Throwable catch-all.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getReason() != null ? ex.getReason() : ex.getMessage()),
                exchange, status), new HttpHeaders(), status);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Throwable ex, ServerWebExchange exchange) {
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
