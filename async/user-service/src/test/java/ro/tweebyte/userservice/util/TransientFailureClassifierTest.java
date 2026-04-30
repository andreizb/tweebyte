package ro.tweebyte.userservice.util;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserNotFoundException;

import java.lang.reflect.Constructor;
import java.sql.SQLTransientException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransientFailureClassifierTest {

    @Test
    void privateConstructorIsAccessibleViaReflection() throws Exception {
        Constructor<TransientFailureClassifier> c = TransientFailureClassifier.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }

    @Test
    void userNotFoundExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new UserNotFoundException("missing")));
    }

    @Test
    void userAlreadyExistsExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new UserAlreadyExistsException("dup")));
    }

    @Test
    void authenticationExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new AuthenticationException("bad")));
    }

    @Test
    void illegalArgumentIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new IllegalArgumentException("bad")));
    }

    @Test
    void validationExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new ValidationException("bad")));
    }

    @Test
    void transientDataAccessIsRetryable() {
        Throwable t = new TransientDataAccessException("transient") {};
        assertTrue(TransientFailureClassifier.isRetryable(t));
    }

    @Test
    void recoverableDataAccessIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new RecoverableDataAccessException("r")));
    }

    @Test
    void concurrencyFailureIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new ConcurrencyFailureException("c")));
    }

    @Test
    void cannotAcquireLockIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new CannotAcquireLockException("lock")));
    }

    @Test
    void queryTimeoutIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new QueryTimeoutException("qt")));
    }

    @Test
    void transientDataAccessResourceIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new TransientDataAccessResourceException("tdr")));
    }

    @Test
    void sqlTransientIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new SQLTransientException("sql")));
    }

    @Test
    void timeoutIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new TimeoutException("to")));
    }

    @Test
    void unknownThrowableIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new RuntimeException("oops")));
    }

    @Test
    void completionExceptionIsUnwrapped() {
        Throwable retryable = new TimeoutException("inner");
        assertTrue(TransientFailureClassifier.isRetryable(new CompletionException(retryable)));

        Throwable nonRetryable = new UserNotFoundException("inner");
        assertFalse(TransientFailureClassifier.isRetryable(new CompletionException(nonRetryable)));
    }

    @Test
    void executionExceptionIsUnwrapped() {
        Throwable retryable = new TimeoutException("inner");
        assertTrue(TransientFailureClassifier.isRetryable(new ExecutionException(retryable)));
    }

    @Test
    void nestedCompletionAndExecutionAreUnwrapped() {
        Throwable inner = new ConcurrencyFailureException("inner");
        Throwable nested = new CompletionException(new ExecutionException(inner));
        assertTrue(TransientFailureClassifier.isRetryable(nested));
    }

    @Test
    void completionExceptionWithNullCauseIsNotRetryable() {
        // CompletionException with no cause - should not unwrap further, returns CompletionException itself
        CompletionException ce = new CompletionException("msg", null) {};
        assertFalse(TransientFailureClassifier.isRetryable(ce));
    }
}
