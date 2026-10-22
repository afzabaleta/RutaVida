
package co.edu.unicesar.rutavida.dto;

/**
 * What a controller returns to the view: success with a value,
 * or failure with a message in Spanish.
 * The view never handles exceptions.
 *
 * @param success true when the operation was done
 * @param message text for the user when it failed;
 *                null when it succeeded
 * @param value the result when it succeeded
 *              (null for operations without a result)
 */
public record OperationResult<T>(
        boolean success,
        String message,
        T value) {

    public OperationResult {
        if (!success && (message == null || message.isBlank())) {
            throw new IllegalArgumentException(
                    "A failed result needs a message for the user");
        }
    }

    public static <T> OperationResult<T> ok(T value) {
        return new OperationResult<>(true, null, value);
    }

    public static <T> OperationResult<T> error(String message) {
        return new OperationResult<>(false, message, null);
    }
}
