package ro.tweebyte.userservice.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());

        return new ResponseEntity<>(getErrorsMap(errors, request, HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());

        return new ResponseEntity<>(getErrorsMap(errors, request, HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND), new HttpHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.UNAUTHORIZED), new HttpHeaders(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleException(Throwable ex, HttpServletRequest request) {
        return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.INTERNAL_SERVER_ERROR), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, Object> getErrorsMap(List<String> errors, HttpServletRequest request, HttpStatus httpStatus) {
        Map<String, Object> errorResponse = new HashMap<>();

        errorResponse.put("timestamp", ZonedDateTime.now().toString());
        errorResponse.put("status", httpStatus.value());
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("errors", errors);

        return errorResponse;
    }


}
