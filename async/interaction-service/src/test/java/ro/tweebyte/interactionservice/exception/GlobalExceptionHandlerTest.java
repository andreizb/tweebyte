package ro.tweebyte.interactionservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises every branch in GlobalExceptionHandler so we don't accept its
 * body shape on faith. Built on the @ControllerAdvice handler directly
 * (no MVC bootstrap) — each handler is just a method returning ResponseEntity.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/test/path");
    }

    @Test
    void handleValidationErrorsReturns400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
            new org.springframework.validation.FieldError("o", "f1", "must not be blank"),
            new org.springframework.validation.FieldError("o", "f2", "must be email")
        ));
        ResponseEntity<Map<String, Object>> resp = handler.handleValidationErrors(ex, req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().get("status"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) resp.getBody().get("errors");
        assertTrue(errors.contains("must not be blank"));
        assertTrue(errors.contains("must be email"));
    }

    @Test
    void handleBindExceptionReturns400() {
        // BindException's getBindingResult() returns itself by default, but Mockito
        // strict-stubs returns null on unstubbed calls — wire it explicitly.
        org.springframework.validation.BindException ex = mock(org.springframework.validation.BindException.class);
        org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
            new org.springframework.validation.FieldError("o", "f", "bad")
        ));
        ResponseEntity<Map<String, Object>> resp = handler.handleBindException(ex, req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void handlePathTypeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getMessage()).thenReturn("not a UUID");
        ResponseEntity<Map<String, Object>> resp = handler.handlePathTypeMismatch(ex, req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) resp.getBody().get("errors");
        assertEquals(List.of("not a UUID"), errors);
    }

    @Test
    void handleIllegalArgumentReturns500() {
        ResponseEntity<Map<String, Object>> resp = handler.handleIllegalArgument(
            new IllegalArgumentException("boom"), req);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) resp.getBody().get("errors");
        assertEquals(List.of("boom"), errors);
    }

    @Test
    void handleTweetNotFoundReturns404() {
        ResponseEntity<Map<String, Object>> resp = handler.handleTweetNotFound(
            new TweetNotFoundException("missing"), req);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handleFollowNotFoundReturns404() {
        ResponseEntity<Map<String, Object>> resp = handler.handleFollowNotFound(
            new FollowNotFoundException("missing"), req);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handleUserNotFoundReturns404() {
        ResponseEntity<Map<String, Object>> resp = handler.handleUserNotFound(
            new UserNotFoundException("missing"), req);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handleInteractionExceptionReturns500() {
        ResponseEntity<Map<String, Object>> resp = handler.handleInteractionException(
            new InteractionException("server error"), req);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }

    @Test
    void handleGenericThrowableReturns500() {
        ResponseEntity<Map<String, Object>> resp = handler.handleException(
            new RuntimeException("oops"), req);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }

    @Test
    void responseBodyAlwaysCarriesPathStatusErrorsAndTimestamp() {
        ResponseEntity<Map<String, Object>> resp = handler.handleException(
            new RuntimeException("body shape probe"), req);
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("/test/path", body.get("path"));
        assertEquals(500, body.get("status"));
        assertNotNull(body.get("timestamp"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) body.get("errors");
        assertEquals(List.of("body shape probe"), errors);
    }
}
