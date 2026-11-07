package co.edu.unicesar.rutavida.exception;

/**
 * There is no session, or it expired: the user must log in again. It is a kind of
 * AccessDeniedException, so the code that already catches that one keeps working; the
 * web API answers 401 for this one and 403 for the other.
 */
public class SessionRequiredException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    public SessionRequiredException(String message) {
        super(message);
    }
}