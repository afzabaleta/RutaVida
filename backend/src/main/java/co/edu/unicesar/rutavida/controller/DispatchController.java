package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.dto.UnitCandidate;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.DispatchService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use cases of the dispatch for the view (CU-08, CU-09, CU-10, CU-12). It checks the session and * the permission of each action, delegates to DispatchService and turns every error into a
 * message in Spanish with its kind (the web API uses it for the HTTP status code).
 */
public class DispatchController {

    private static final Logger LOG = Logger.getLogger(DispatchController.class.getName());

    static final String DISPATCH = "DESPACHO";
    static final String ROUTES = "RUTAS";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final DispatchService dispatchService;
    private final AuthService authService;
    private final SessionContext session;

    public DispatchController(DispatchService dispatchService, AuthService authService,
                              SessionContext session) {
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** CU-09, step 2: units that can go, fastest first. Needs DESPACHO / GESTIONAR. */
    public OperationResult<List<UnitCandidate>> candidates(long emergencyId) {
        return run(DISPATCH, MANAGE, user -> dispatchService.candidates(emergencyId));
    }

    /** CU-08: routes before confirming, nothing is saved. Needs RUTAS / GESTIONAR. */
    public OperationResult<List<RouteOption>> preview(long emergencyId, long vehicleId) {
        return run(ROUTES, MANAGE, user -> dispatchService.preview(emergencyId, vehicleId));
    }

    /** CU-09: sends the unit. Needs DESPACHO / GESTIONAR. */
    public OperationResult<DispatchResult> dispatch(long emergencyId, long vehicleId) {
        return run(DISPATCH, MANAGE, user -> dispatchService.dispatch(emergencyId, vehicleId, user));
    }

    /** CU-10: the mission advances. Needs DESPACHO / GESTIONAR. */
    public OperationResult<EmergencyAssignment> advance(long assignmentId, AssignmentStatus next) {
        return run(DISPATCH, MANAGE, user -> dispatchService.advance(assignmentId, next));
    }

    /** CU-12: closes the emergency and frees its units. Needs DESPACHO / GESTIONAR. */
    public OperationResult<Emergency> close(long emergencyId) {
        return run(DISPATCH, MANAGE, user -> dispatchService.closeEmergency(emergencyId, user));
    }

    /** Routes still in use by a mission. Needs RUTAS / CONSULTAR. */
    public OperationResult<List<Route>> missionRoutes(long assignmentId) {
        return run(ROUTES, VIEW, user -> dispatchService.missionRoutes(assignmentId));
    }

    /** Checks the session and permission, runs the work and converts errors into messages. */
    private <T> OperationResult<T> run(String module, String action, Function<UserSession, T> work) {
        if (!session.isActive()) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }

        try {
            UserSession user = session.current();
            authService.requirePermission(user, module, action);
            return OperationResult.ok(work.apply(user));
        } catch (RuntimeException e) {
            OperationResult<T> failure = OperationResult.failure(e);
            if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Dispatch operation failed", e);
            }
            return failure;
        }
    }
}
