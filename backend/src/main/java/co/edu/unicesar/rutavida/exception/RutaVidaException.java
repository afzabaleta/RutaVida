package co.edu.unicesar.rutavida.exception;

/**
 * Base of every RutaVida exception. Unchecked, so layers do not have to declare
 * technical exceptions they cannot handle. Controllers catch it and show a
 * Spanish message to the user (see util messages).
 */
public class RutaVidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RutaVidaException(String message) {
        super(message);
    }

    public RutaVidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
