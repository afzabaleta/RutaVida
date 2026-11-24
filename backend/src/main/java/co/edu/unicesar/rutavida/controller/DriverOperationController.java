package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DriverMission;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.DriverOperationService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.Objects;
import java.util.function.Function;

/** Driver-only operational endpoints for the Compartir ubicación screen. */
public final class DriverOperationController {

    public static final String MODULE = "GPS";
    public static final String OPERATE = "OPERAR";

    private final DriverOperationService operations;
    private final AuthService auth;
    private final SessionContext session;

    public DriverOperationController(DriverOperationService operations, AuthService auth, SessionContext session) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.session = Objects.requireNonNull(session, "session");
    }

    public OperationResult<DriverMission> current() {
        return run(GpsController.REPORT, operations::current);
    }

    public OperationResult<DriverMission> startGps() {
        return run(OPERATE, operations::startGps);
    }

    public OperationResult<DriverMission> stopGps() {
        return run(OPERATE, operations::stopGps);
    }

    public OperationResult<DriverMission> confirmArrival() {
        return run(OPERATE, operations::confirmArrival);
    }

    public OperationResult<DriverMission> finishAttention() {
        return run(OPERATE, operations::finishAttention);
    }

    private OperationResult<DriverMission> run(String permission, Function<UserSession, DriverMission> work) {
        if (!session.isActive()) return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        try {
            UserSession user = session.current();
            auth.requirePermission(user, MODULE, permission);
            return OperationResult.ok(work.apply(user));
        } catch (RuntimeException error) {
            return OperationResult.failure(error);
        }
    }
}
