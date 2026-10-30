package co.edu.unicesar.rutavida.util;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;

/**
 * The texts the user sees, in Spanish, in one place.
 * Controllers use userMessage so technical errors never reach the screen.
 */
public final class Messages {

    public static final String DATABASE_DOWN = "No hay conexión con la base de datos.";
    public static final String NOT_FOUND = "No se encontró la información solicitada.";
    public static final String UNEXPECTED = "Ocurrió un error inesperado. Intente de nuevo.";
    public static final String SESSION_REQUIRED = "Debe iniciar sesión.";

    private Messages() {
    }

    /** Converts an exception into a message for the user. */
    public static String userMessage(RuntimeException error) {
        if (error instanceof ValidationException
                || error instanceof BusinessRuleException
                || error instanceof AccessDeniedException) {
            return error.getMessage();
        }
        if (error instanceof DataAccessException) {
            return DATABASE_DOWN;
        }
        if (error instanceof NotFoundException) {
            return NOT_FOUND;
        }
        return UNEXPECTED;
    }
}
