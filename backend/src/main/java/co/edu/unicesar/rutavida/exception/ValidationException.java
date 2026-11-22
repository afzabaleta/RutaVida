package co.edu.unicesar.rutavida.exception;

/** Input data is missing or invalid (e.g. latitude out of range, empty address). */
public class ValidationException extends RutaVidaException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
