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

import java.sql.SQLTransientException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for TransientFailureClassifier — every "should retry?"
 * decision and every unwrap path.
 */
class TransientFailureClassifierTest {

    // --- non-retryable application-level exceptions ----------------

    @Test
    void userNotFoundIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new UserNotFoundException("x")));
    }

    @Test
    void userAlreadyExistsIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new UserAlreadyExistsException("x")));
    }

    @Test
    void authenticationExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new AuthenticationException("x")));
    }

    @Test
    void illegalArgumentIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new IllegalArgumentException("x")));
    }

    @Test
    void validationExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new ValidationException("x")));
    }

    // --- retryable transient exceptions ----------------------------

    @Test
    void transientDataAccessIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new TransientDataAccessException("t") {}));
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
        assertTrue(TransientFailureClassifier.isRetryable(new CannotAcquireLockException("c")));
    }

    @Test
    void queryTimeoutIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new QueryTimeoutException("q")));
    }

    @Test
    void transientDataAccessResourceIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new TransientDataAccessResourceException("r")));
    }

    @Test
    void sqlTransientIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new SQLTransientException("s")));
    }

    @Test
    void timeoutExceptionIsRetryable() {
        assertTrue(TransientFailureClassifier.isRetryable(new TimeoutException("t")));
    }

    @Test
    void plainRuntimeExceptionIsNotRetryable() {
        assertFalse(TransientFailureClassifier.isRetryable(new RuntimeException("nope")));
    }

    // --- unwrapping -------------------------------------------------

    @Test
    void completionExceptionUnwrapsToInnerCause() {
        Throwable inner = new TimeoutException("inner");
        Throwable wrapped = new CompletionException(inner);
        assertTrue(TransientFailureClassifier.isRetryable(wrapped));
    }

    @Test
    void executionExceptionUnwrapsToInnerCause() {
        Throwable inner = new TimeoutException("inner");
        Throwable wrapped = new ExecutionException(inner);
        assertTrue(TransientFailureClassifier.isRetryable(wrapped));
    }

    @Test
    void executionExceptionUnwrappingPreservesNonRetryable() {
        Throwable wrapped = new ExecutionException(new UserNotFoundException("nope"));
        assertFalse(TransientFailureClassifier.isRetryable(wrapped));
    }

    @Test
    void completionExceptionWithNullCauseHandled() {
        // unwrap stops when cause is null
        CompletionException ce = new CompletionException(null);
        // The outer is CompletionException but its cause is null → unwrap returns it as-is.
        // CompletionException is none of the retryable types → false.
        assertFalse(TransientFailureClassifier.isRetryable(ce));
    }

    @Test
    void deeplyNestedCompletionAndExecutionUnwrapped() {
        Throwable inner = new SQLTransientException("deep");
        Throwable level1 = new ExecutionException(inner);
        Throwable level2 = new CompletionException(level1);
        assertTrue(TransientFailureClassifier.isRetryable(level2));
    }
}
