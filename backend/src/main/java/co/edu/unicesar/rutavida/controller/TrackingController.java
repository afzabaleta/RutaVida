package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.TrackingSnapshot;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.LiveMapService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The live map (CU-07, CU-18): needs EMERGENCIAS/CONSULTAR, like the list of emergencies. */
public class TrackingController {

    private static final Logger LOG = Logger.getLogger(TrackingController.class.getName());

    static final String MODULE = "EMERGENCIAS";
    static final String VIEW = "CONSULTAR";

    private final LiveMapService liveMapService;
    private final AuthService authService;
    private final SessionContext session;

    public TrackingController(LiveMapService liveMapService, AuthService authService, SessionContext session) {
        this.liveMapService = Objects.requireNonNull(liveMapService, "liveMapService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Where the units are, the open missions and the unread alerts of the user. */
    public OperationResult<TrackingSnapshot> snapshot() {
        if (!session.isActive()) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }
        try {
            UserSession user = session.current();
            authService.requirePermission(user, MODULE, VIEW);
            return OperationResult.ok(liveMapService.snapshot(user));
        } catch (RuntimeException e) {
            OperationResult<TrackingSnapshot> failure = OperationResult.failure(e);
            if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "Tracking query failed", e);
            }
            return failure;
        }
    }
}
