
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.EmergencyHistory;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.EmergencyService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use cases of emergencies for the view (CU-06, CU-07, CU-15).
 * It checks the session and the permission, delegates to EmergencyService
 * and turns every error into a message in Spanish.
 */
public class EmergencyController {

    private static final Logger LOG =
            Logger.getLogger(EmergencyController.class.getName());

    static final String MODULE = "EMERGENCIAS";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final EmergencyService emergencyService;
    private final AuthService authService;
    private final SessionContext session;

    public EmergencyController(
            EmergencyService emergencyService,
            AuthService authService,
            SessionContext session) {

        this.emergencyService = Objects.requireNonNull(
                emergencyService, "emergencyService");

        this.authService = Objects.requireNonNull(
                authService, "authService");

        this.session = Objects.requireNonNull(
                session, "session");
    }

    /** CU-06: needs EMERGENCIAS / GESTIONAR. */
    public OperationResult<Emergency> register(EmergencyRequest request) {
        return run(MANAGE, user -> emergencyService.register(request, user));
    }

    /** CU-07: needs EMERGENCIAS / CONSULTAR. */
    public OperationResult<List<Emergency>> listActive() {
        return run(VIEW, user -> emergencyService.listActive());
    }

    /** CU-15: needs EMERGENCIAS / CONSULTAR. */
    public OperationResult<EmergencyHistory> history(long emergencyId) {
        return run(VIEW, user -> emergencyService.getHistory(emergencyId));
    }

    /** Checks the session and permission, runs the work and converts errors into messages. */
    private <T> OperationResult<T> run(
            String action,
            Function<UserSession, T> work) {

        if (!session.isActive()) {
            return OperationResult.error(Messages.SESSION_REQUIRED);
        }

        try {
            UserSession user = session.current();

            authService.requirePermission(user, MODULE, action);

            return OperationResult.ok(work.apply(user));

        } catch (RuntimeException e) {

            String message = Messages.userMessage(e);

            if (Messages.UNEXPECTED.equals(message)
                    || Messages.DATABASE_DOWN.equals(message)) {

                LOG.log(Level.WARNING, "Emergency operation failed", e);
            }

            return OperationResult.error(message);
        }
    }
}
