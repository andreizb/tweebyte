package ro.tweebyte.userservice.util;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserNotFoundException;

import jakarta.validation.ValidationException;
import java.sql.SQLTransientException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class TransientFailureClassifier {

    private TransientFailureClassifier() {
    }

    public static boolean isRetryable(Throwable throwable) {
        Throwable cause = unwrap(throwable);

        if (cause instanceof UserNotFoundException
            || cause instanceof UserAlreadyExistsException
            || cause instanceof AuthenticationException
            || cause instanceof IllegalArgumentException
            || cause instanceof ValidationException) {
            return false;
        }

        return cause instanceof TransientDataAccessException
            || cause instanceof RecoverableDataAccessException
            || cause instanceof ConcurrencyFailureException
            || cause instanceof CannotAcquireLockException
            || cause instanceof QueryTimeoutException
            || cause instanceof TransientDataAccessResourceException
            || cause instanceof SQLTransientException
            || cause instanceof TimeoutException;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
