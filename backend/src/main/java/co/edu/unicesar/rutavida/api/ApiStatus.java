package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;

/** HTTP status code of each result (docs/api.md, section 1.2). */
public final class ApiStatus {

    public static final int OK = 200;
    public static final int CREATED = 201;

    private ApiStatus() {
    }

    public static int of(ErrorKind kind) {
        if (kind == null) {
            return 500;
        }
        return switch (kind) {
            case VALIDATION -> 400;
            case AUTHENTICATION -> 401;
            case PERMISSION -> 403;
            case NOT_FOUND -> 404;
            case BUSINESS_RULE -> 409;
            case UNAVAILABLE -> 503;
            case UNEXPECTED -> 500;
        };
    }

    /** {@code successStatus} when it succeeded (200 or 201), otherwise the code of its error. */
    public static int of(OperationResult<?> result, int successStatus) {
        return result.success() ? successStatus : of(result.error());
    }
}