
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.PanicReportView;
import co.edu.unicesar.rutavida.dto.PanicResult;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.PanicService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panic button (CU-22, DT-13). Reporting needs a session with PANICO/REPORTAR: a registered
 * citizen (whose phone stays logged in), a police officer or an administrator. Listing
 * needs PANICO/CONSULTAR and discarding PANICO/GESTIONAR.
 */
public class PanicController {

    private static final Logger LOG = Logger.getLogger(PanicController.class.getName());

    static final String MODULE = "PANICO";
    static final String REPORT = "REPORTAR";
    static final String VIEW = "CONSULTAR";
    static final String MANAGE = "GESTIONAR";

    static final String HELP_ON_ITS_WAY = "Su alerta fue validada: la ayuda va en camino.";
    static final String WAITING_FOR_DISPATCH =
            "Su alerta fue validada. No hay ambulancias elegibles con GPS activo. Quedó pendiente para coordinación.";

    private final PanicService panicService;
    private final AuthService authService;
    private final SessionContext session;

    public PanicController(PanicService panicService, AuthService authService,
                           SessionContext session) {
        this.panicService = Objects.requireNonNull(panicService, "panicService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** CU-22: a registered person pressed the button. */
    public OperationResult<PanicResult> report(PanicAlertRequest request) {
        OperationResult<PanicResult> result = run(REPORT, user -> panicService.report(request, user));
        return result.success()
                ? new OperationResult<>(true, messageFor(result.value()), result.value())
                : result;
    }

    /** Reports of the last 2 hours for the map; all of them when {@code status} is null. */
    public OperationResult<List<PanicReportView>> listRecent(PanicReportStatus status) {
        return run(VIEW, user -> panicService.listRecent(status));
    }

    /** CU-22, 7a: a coordinator marks a pending alert as false. */
    public OperationResult<PanicReportView> discard(long reportId) {
        return run(MANAGE, user -> panicService.discard(reportId, user));
    }

    /** What the page tells the person after pressing. */
    static String messageFor(PanicResult result) {
        if (!result.createdEmergency()) {
            return "Su reporte se vinculó a una emergencia abierta.";
        }
        return result.dispatchedUnit() == null ? WAITING_FOR_DISPATCH : HELP_ON_ITS_WAY;
    }

    private <T> OperationResult<T> run(String action, Function<UserSession, T> work) {
        if (!session.isActive()) {
            return OperationResult.error(ErrorKind.AUTHENTICATION, Messages.SESSION_REQUIRED);
        }
        try {
            UserSession user = session.current();
            authService.requirePermission(user, MODULE, action);
            return OperationResult.ok(work.apply(user));
        } catch (RuntimeException e) {
            return failure(e);
        }
    }

    private static <T> OperationResult<T> failure(RuntimeException e) {
        OperationResult<T> failure = OperationResult.failure(e);
        if (failure.error() == ErrorKind.UNEXPECTED || failure.error() == ErrorKind.UNAVAILABLE) {
            LOG.log(Level.WARNING, "Panic operation failed", e);
        }
        return failure;
    }
}
