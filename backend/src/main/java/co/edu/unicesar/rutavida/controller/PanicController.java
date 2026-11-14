
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
 * Panic button (CU-22). Reporting needs no session: a citizen presses it without an
 * account, and a police officer with a session validates the alert alone. Listing needs
 * PANICO/CONSULTAR and discarding PANICO/GESTIONAR.
 */
public class PanicController {

    private static final Logger LOG = Logger.getLogger(PanicController.class.getName());

    static final String MODULE = "PANICO";
    static final String VIEW = "CONSULTAR";
    static final String MANAGE = "GESTIONAR";

    static final String HELP_ON_ITS_WAY = "Su alerta fue validada: la ayuda va en camino.";
    static final String WAITING_FOR_DISPATCH =
            "Su alerta fue validada. Un coordinador enviará una unidad.";
    static final String WAITING_FOR_OTHERS =
            "Su reporte quedó registrado. Esperamos la confirmación de otras personas cercanas.";

    private final PanicService panicService;
    private final AuthService authService;
    private final SessionContext session;

    public PanicController(PanicService panicService, AuthService authService,
                           SessionContext session) {
        this.panicService = Objects.requireNonNull(panicService, "panicService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** CU-22: someone pressed the button. The session is used only if there is one. */
    public OperationResult<PanicResult> report(PanicAlertRequest request) {
        try {
            UserSession reporter = session.isActive() ? session.current() : null;
            PanicResult result = panicService.report(request, reporter);
            return new OperationResult<>(true, messageFor(result), result);
        } catch (RuntimeException e) {
            return failure(e);
        }
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
        if (result.status() == PanicReportStatus.PENDING) {
            return WAITING_FOR_OTHERS;
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
