package ro.tweebyte.tweetservice.exception;

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
 * Returns the same response shape as the async/tweet-service handler so the
 * FE Cucumber suite observes identical 4xx/5xx bodies on both stacks.
 *
 * Body shape: { "timestamp": "...", "status": <int>, "path": "<path>", "errors": ["..."] }
 *
 * <p>The TweetNotFoundException handler maps missing-tweet errors to 404 with
 * the structured body above so callers (gateway-routed clients,
 * cross-service callers) see a 404 instead of a 500 + stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTweetNotFound(TweetNotFoundException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
                new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TweetException.class)
    public ResponseEntity<Map<String, Object>> handleTweet(TweetException ex, ServerWebExchange exchange) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(WebExchangeBindException ex, ServerWebExchange exchange) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ResponseEntity<>(getErrorsMap(errors, exchange, HttpStatus.BAD_REQUEST),
                new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    /**
     * WebFlux's default behaviour for a malformed @PathVariable UUID is to
     * raise ResponseStatusException(400, "Type mismatch."). Our Throwable
     * catch-all below intercepts that BEFORE Spring's default handler can honour
     * the embedded 400, so without this specific handler the path returns 500.
     * Match the async tweet-service GlobalExceptionHandler's 400 mapping.
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
