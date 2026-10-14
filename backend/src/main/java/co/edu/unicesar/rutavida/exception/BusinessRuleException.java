package co.edu.unicesar.rutavida.exception;

/**
 * The operation is valid as data but breaks a business rule
 * (e.g. dispatching a unit that is not AVAILABLE, closing an emergency twice).
 */
public class BusinessRuleException extends RutaVidaException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
