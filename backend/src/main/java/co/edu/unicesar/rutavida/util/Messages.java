
package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;

import java.util.Map;

/**
 * Catalog of the texts the user sees, in Spanish, in one place.
 * Controllers use userMessage so technical errors never reach the screen.
 */
public final class Messages {

    public static final String DATABASE_DOWN = "error.database";
    public static final String NOT_FOUND = "error.notFound";
    public static final String UNEXPECTED = "error.unexpected";
    public static final String SESSION_REQUIRED = "session.required";

    private static final Map<String, String> TEXTS = Map.of(
            DATABASE_DOWN, "No hay conexión con la base de datos.",
            NOT_FOUND, "No se encontró la información solicitada.",
            UNEXPECTED, "Ocurrió un error inesperado. Intente de nuevo.",
            SESSION_REQUIRED, "Debe iniciar sesión."
    );

    private Messages() {
    }

    /**
     * Returns the Spanish text associated with a message key.
     */
    public static String get(String key) {
        String text = TEXTS.get(key);

        if (text == null) {
            throw new IllegalArgumentException(
                    "Unknown message key: " + key);
        }

        return text;
    }

    /**
     * Converts exceptions into user-facing Spanish messages.
     */
    public static String userMessage(RuntimeException error) {

        if (error instanceof ValidationException
                || error instanceof BusinessRuleException
                || error instanceof AccessDeniedException) {
            return error.getMessage();
        }

        if (error instanceof DataAccessException) {
            return get(DATABASE_DOWN);
        }

        if (error instanceof NotFoundException) {
            return get(NOT_FOUND);
        }

        return get(UNEXPECTED);
    }
}
