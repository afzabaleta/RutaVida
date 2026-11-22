package co.edu.unicesar.rutavida.exception;

/** A record that must exist was not found (e.g. emergency id 99 does not exist). */
public class NotFoundException extends RutaVidaException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
