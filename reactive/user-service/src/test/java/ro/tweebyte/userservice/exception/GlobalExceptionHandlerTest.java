package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the reactive user-service GlobalExceptionHandler. The reactive handler accepts
 * ServerWebExchange instead of HttpServletRequest, but the assertions on body shape
 * (timestamp / status / path / errors) and HTTP status code remain identical.
 */
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
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

        WebExchangeBindException ex = new WebExchangeBindException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> responseEntity =
                globalExceptionHandler.handleValidationErrors(ex, exchange("/test"));

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(String.class, responseBody.get("timestamp").getClass());
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(2, errors.size());
        assertEquals("Error Message 1", errors.get(0));
        assertEquals("Error Message 2", errors.get(1));
    }

    @Test
    void handleBindException() {
        // Reactive WebFlux folds BindException into WebExchangeBindException; the
        // async test exercises the same handler for both code paths.
        FieldError fieldError1 = new FieldError("objectName", "fieldName1", "Error Message 1");
        FieldError fieldError2 = new FieldError("objectName", "fieldName2", "Error Message 2");

        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objectName");
        bindingResult.addError(fieldError1);
        bindingResult.addError(fieldError2);

        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterName()).thenReturn("parameterName");

        WebExchangeBindException ex = new WebExchangeBindException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> responseEntity =
                globalExceptionHandler.handleValidationErrors(ex, exchange("/test"));

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(String.class, responseBody.get("timestamp").getClass());
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(2, errors.size());
        assertEquals("Error Message 1", errors.get(0));
        assertEquals("Error Message 2", errors.get(1));
    }

    @Test
    void handleUserAlreadyExistsException() {
        // duplicate email/username surfaces as UserAlreadyExistsException → 400.
        String errorMessage = "User already exists";
        UserAlreadyExistsException ex = new UserAlreadyExistsException(errorMessage);

        ResponseEntity<Map<String, Object>> responseEntity =
                globalExceptionHandler.handleUserAlreadyExists(ex, exchange("/test"));

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

    @Test
    void handleUserNotFoundException() {
        String errorMessage = "User not found";
        UserNotFoundException ex = new UserNotFoundException(errorMessage);

        ResponseEntity<Map<String, Object>> responseEntity =
                globalExceptionHandler.handleUserNotFound(ex, exchange("/test"));

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

    @Test
    void handleAuthenticationException() {
        // AuthenticationException must surface as 401.
        String errorMessage = "Authentication failed";
        AuthenticationException ex = new AuthenticationException(errorMessage);

        ResponseEntity<Map<String, Object>> responseEntity =
                globalExceptionHandler.handleAuthentication(ex, exchange("/test"));

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.UNAUTHORIZED.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

    @Test
    void handleException() {
        // Reactive analogue to async's catch-all: UserException → 500 with the same
        // body shape as the rest of the handlers.
        String errorMessage = "An unexpected error occurred";
        UserException ex = new UserException(errorMessage);

        ResponseEntity<Map<String, Object>> responseEntity =
                globalExceptionHandler.handleUser(ex, exchange("/test"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        Map<String, Object> responseBody = responseEntity.getBody();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), responseBody.get("status"));
        assertEquals("/test", responseBody.get("path"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) responseBody.get("errors");
        assertEquals(1, errors.size());
        assertEquals(errorMessage, errors.get(0));
    }

}
