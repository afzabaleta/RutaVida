package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.dto.OperationResult;

/**
 * The JSON body of every response (docs/api.md, 1.1): {@code {success, message, value}}.
 * The kind of error does not travel in the body: it is the HTTP status code.
 */
public record ApiResponse(boolean success, String message, Object value) {

    public static ApiResponse of(OperationResult<?> result) {
        return new ApiResponse(result.success(), result.message(), result.value());
    }
}