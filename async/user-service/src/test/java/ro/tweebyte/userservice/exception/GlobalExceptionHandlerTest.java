package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void handleValidationErrors() {
        FieldError fieldError1 = new FieldError("objectName", "fieldName1", "Error Message 1");
        FieldError fieldError2 = new FieldError("objectName", "fieldName2", "Error Message 2");

        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objectName");
        bindingResult.addError(fieldError1);
        bindingResult.addError(fieldError2);

        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterName()).thenReturn("parameterName");

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<Map<String, Object>> responseEntity = globalExceptionHandler.handleValidationErrors(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(String.class, responseBody.get("timestamp").getClass());
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(2, errors.size());
        assertEquals("Error Message 1", errors.get(0));
        assertEquals("Error Message 2", errors.get(1));
    }

    @Test
    void handleBindException() {
        FieldError fieldError1 = new FieldError("objectName", "fieldName1", "Error Message 1");
        FieldError fieldError2 = new FieldError("objectName", "fieldName2", "Error Message 2");

        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objectName");
        bindingResult.addError(fieldError1);
        bindingResult.addError(fieldError2);

        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterName()).thenReturn("parameterName");

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<Map<String, Object>> responseEntity = globalExceptionHandler.handleBindException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(String.class, responseBody.get("timestamp").getClass());
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(2, errors.size());
        assertEquals("Error Message 1", errors.get(0));
        assertEquals("Error Message 2", errors.get(1));
    }

    @Test
    void handleUserAlreadyExistsException() {
        String errorMessage = "User already exists";
        UserAlreadyExistsException ex = new UserAlreadyExistsException(errorMessage);

        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<Map<String, Object>> responseEntity = globalExceptionHandler.handleUserAlreadyExists(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

    @Test
    void handleUserNotFoundException() {
        String errorMessage = "User not found";
        UserNotFoundException ex = new UserNotFoundException(errorMessage);

        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<Map<String, Object>> responseEntity = globalExceptionHandler.handleUserNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

    @Test
    void handleAuthenticationException() {
        String errorMessage = "Authentication failed";
        AuthenticationException ex = new AuthenticationException(errorMessage);

        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<Map<String, Object>> responseEntity = globalExceptionHandler.handleAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.UNAUTHORIZED.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

    @Test
    void handleException() {
        String errorMessage = "An unexpected error occurred";
        Throwable ex = new RuntimeException(errorMessage);

        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<Map<String, Object>> responseEntity = globalExceptionHandler.handleException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

}