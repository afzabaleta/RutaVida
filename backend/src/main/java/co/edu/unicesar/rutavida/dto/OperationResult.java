package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.SessionRequiredException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.util.Messages;
import co.edu.unicesar.rutavida.exception.AccessDeniedException;

/**
 * What a controller returns to the view: success with a value,
 * or failure with a message in Spanish and the kind of error.
 * The view never handles exceptions.
 *
 * @param success true when the operation was done
 * @param message text for the user when it failed;
 *                null when it succeeded
 * @param value the result when it succeeded
 *              (null for operations without a result)
 * @param error why it failed (null when it succeeded); the web API
 *              uses it to choose the HTTP status code
 */
public record OperationResult<T>(
        boolean success,
        String message,
        T value,
        ErrorKind error) {

    public OperationResult {
        if (!success && (message == null || message.isBlank())) {
            throw new IllegalArgumentException(
                    "A failed result needs a message for the user");
        }
        if (success) {
            error = null;
        } else if (error == null) {
            error = ErrorKind.UNEXPECTED;
        }
    }

    /** Kept for the code written before {@link ErrorKind} existed. */
    public OperationResult(boolean success, String message, T value) {
        this(success, message, value, null);
    }

    public static <T> OperationResult<T> ok(T value) {
        return new OperationResult<>(true, null, value, null);
    }

    /** A failure of an unknown kind. Prefer {@link #failure(RuntimeException)}. */
    public static <T> OperationResult<T> error(String message) {
        return new OperationResult<>(false, message, null, ErrorKind.UNEXPECTED);
    }

    public static <T> OperationResult<T> error(ErrorKind kind, String message) {
        return new OperationResult<>(false, message, null, kind);
    }

    /**
     * Converts an exception into a failed result: the message for the user comes from
     * {@link Messages#userMessage} and the kind from the type of the exception.
     */
    public static <T> OperationResult<T> failure(RuntimeException exception) {
        return new OperationResult<>(false, Messages.userMessage(exception), null, kindOf(exception));
    }

    static ErrorKind kindOf(RuntimeException exception) {
        if (exception instanceof ValidationException) {
            return ErrorKind.VALIDATION;
        }
        if (exception instanceof SessionRequiredException) {
            return ErrorKind.AUTHENTICATION;
        }
        if (exception instanceof AccessDeniedException) {
            return ErrorKind.PERMISSION;
        }
        if (exception instanceof NotFoundException) {
            return ErrorKind.NOT_FOUND;
        }
        if (exception instanceof BusinessRuleException) {
            return ErrorKind.BUSINESS_RULE;
        }
        if (exception instanceof DataAccessException) {
            return ErrorKind.UNAVAILABLE;
        }
        return ErrorKind.UNEXPECTED;
    }
}