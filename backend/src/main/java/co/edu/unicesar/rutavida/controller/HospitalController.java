
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.HospitalRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.HospitalService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hospital management for the view (CU-05). It checks the session and the permission,
 * delegates to HospitalService and converts errors into messages in Spanish with their kind.
 */
public class HospitalController {

    private static final Logger LOG = Logger.getLogger(HospitalController.class.getName());

    static final String MODULE = "HOSPITALES";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final HospitalService hospitalService;
    private final AuthService authService;
    private final SessionContext session;

    public HospitalController(HospitalService hospitalService, AuthService authService,
                              SessionContext session) {
        this.hospitalService = Objects.requireNonNull(hospitalService, "hospitalService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Active hospitals: the possible destinations of a transfer. */
    public OperationResult<List<Hospital>> listOperational() {
        return run(VIEW, user -> hospitalService.listOperational());
    }

    /** CU-05: registers a hospital. */
    public OperationResult<Hospital> register(HospitalRequest request) {
        return run(MANAGE, user -> hospitalService.register(request, user));
    }

    /** CU-05: edits the data of a hospital. */
    public OperationResult<Hospital> update(long hospitalId, HospitalRequest request) {
        return run(MANAGE, user -> hospitalService.update(hospitalId, request, user));
    }

    /** CU-05: activates or deactivates a hospital. */
    public OperationResult<Hospital> changeStatus(long hospitalId, RecordStatus status) {
        return run(MANAGE, user -> hospitalService.changeStatus(hospitalId, status, user));
    }

    private <T> OperationResult<T> run(String action, Function<UserSession, T> work) {
        if (!session.isActive()) {
            return OperationResult.error(
                    ErrorKind.AUTHENTICATION,
                    Messages.SESSION_REQUIRED
            );
        }

        try {
            UserSession user = session.current();
            authService.requirePermission(user, MODULE, action);
            return OperationResult.ok(work.apply(user));

        } catch (RuntimeException e) {
            OperationResult<T> failure = OperationResult.failure(e);

            if (failure.error() == ErrorKind.UNEXPECTED
                    || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Hospital operation failed", e);
            }

            return failure;
        }
    }
}
