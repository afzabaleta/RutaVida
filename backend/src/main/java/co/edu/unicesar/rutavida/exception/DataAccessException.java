package co.edu.unicesar.rutavida.exception;

/**
 * Wraps any SQLException so that JDBC types never leave the dao.jdbc package.
 * The message is technical (for logs); the user sees a generic Spanish message.
 */
public class DataAccessException extends RutaVidaException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
