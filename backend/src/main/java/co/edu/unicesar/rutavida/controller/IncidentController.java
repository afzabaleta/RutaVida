package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.IncidentService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use cases of road incidents for the view (CU-13, CU-14).
 *
 * It checks the session and permission, delegates to IncidentService
 * and converts errors into messages in Spanish with their error kind.
 */
public class IncidentController {

    private static final Logger LOG =
            Logger.getLogger(IncidentController.class.getName());

    static final String MODULE = "INCIDENTES";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final IncidentService incidentService;
    private final AuthService authService;
    private final SessionContext session;

    public IncidentController(
            IncidentService incidentService,
            AuthService authService,
            SessionContext session) {

        this.incidentService = Objects.requireNonNull(
                incidentService, "incidentService");

        this.authService = Objects.requireNonNull(
                authService, "authService");

        this.session = Objects.requireNonNull(
                session, "session");
    }

    /**
     * CU-13: Report an incident.
     */
    public OperationResult<RoadIncident> report(IncidentRequest request) {
        return run(MANAGE,
                user -> incidentService.report(
                        request,
                        IncidentOrigin.OPERATOR,
                        user
                )
        );
    }

    /**
     * CU-14: Activate, reject or close an incident.
     */
    public OperationResult<RoadIncident> changeStatus(
            long incidentId,
            IncidentStatus next) {

        return run(MANAGE,
                user -> incidentService.changeStatus(incidentId, next)
        );
    }

    /**
     * Consult pending and active incidents.
     */
    public OperationResult<List<RoadIncident>> listOpen() {
        return run(VIEW,
                user -> incidentService.listOpen()
        );
    }

    /**
     * Consult incidents by status.
     */
    public OperationResult<List<RoadIncident>> listByStatus(
            IncidentStatus status) {

        return run(VIEW,
                user -> incidentService.listByStatus(status)
        );
    }

    /**
     * Find one incident by its ID.
     */
    public OperationResult<RoadIncident> find(long incidentId) {
        return run(VIEW,
                user -> incidentService.findById(incidentId)
        );
    }

    /**
     * Checks the session and permission, executes the operation
     * and converts errors into OperationResult.
     */
    private <T> OperationResult<T> run(
            String action,
            Function<UserSession, T> work) {

        if (!session.isActive()) {
            return OperationResult.error(
                    ErrorKind.AUTHENTICATION,
                    Messages.SESSION_REQUIRED
            );
        }

        try {
            UserSession user = session.current();

            authService.requirePermission(
                    user,
                    MODULE,
                    action
            );

            return OperationResult.ok(work.apply(user));

        } catch (RuntimeException e) {

            OperationResult<T> failure =
                    OperationResult.failure(e);

            if (failure.error() == ErrorKind.UNEXPECTED
                    || failure.error() == ErrorKind.UNAVAILABLE) {

                LOG.log(
                        Level.WARNING,
                        "Incident operation failed",
                        e
                );
            }

            return failure;
        }
    }
}