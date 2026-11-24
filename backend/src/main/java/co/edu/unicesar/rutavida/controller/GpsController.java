package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DriverUnit;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.GpsPosition;
import co.edu.unicesar.rutavida.dto.GpsPositionRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.GpsTrackingService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.Objects;
import java.util.function.Function;

/** GPS endpoints for the field driver assigned to an emergency unit. */
public final class GpsController {

    public static final String MODULE = "GPS";
    public static final String REPORT = "REPORTAR";

    private final GpsTrackingService tracking;
    private final AuthService auth;
    private final SessionContext session;

    public GpsController(GpsTrackingService tracking, AuthService auth, SessionContext session) {
        this.tracking = Objects.requireNonNull(tracking, "tracking");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.session = Objects.requireNonNull(session, "session");
    }

    public OperationResult<DriverUnit> myUnit() {
        return run(tracking::unitOf);
    }

    public OperationResult<GpsPosition> report(GpsPositionRequest request) {
        return run(user -> tracking.record(user, request));
    }

    /** Resolves a socket subscriber from its authenticated session and current driver link. */
    public long assignedVehicleId(UserSession user) {
        auth.requirePermission(user, MODULE, REPORT);
        return tracking.unitOf(user).vehicleId();
    }

    private <T> OperationResult<T> run(Function<UserSession, T> work) {
        if (!session.isActive()) return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        try {
            UserSession user = session.current();
            auth.requirePermission(user, MODULE, REPORT);
            return OperationResult.ok(work.apply(user));
        } catch (RuntimeException error) {
            return OperationResult.failure(error);
        }
    }
}
