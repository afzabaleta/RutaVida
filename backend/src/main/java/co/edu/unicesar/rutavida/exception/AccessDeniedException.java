
package co.edu.unicesar.rutavida.exception;

/**
 * The current user does not have the permission required for the operation.
 */
public class AccessDeniedException extends RutaVidaException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}

