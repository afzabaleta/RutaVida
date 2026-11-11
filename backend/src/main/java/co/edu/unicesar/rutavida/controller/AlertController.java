
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alerts of the logged-in user for the view.
 * Any active session can read its own alerts.
 */
public class AlertController {

    private static final Logger LOG =
            Logger.getLogger(AlertController.class.getName());

    private final AlertService alertService;
    private final SessionContext session;

    public AlertController(
            AlertService alertService,
            SessionContext session) {

        this.alertService = Objects.requireNonNull(
                alertService, "alertService");

        this.session = Objects.requireNonNull(
                session, "session");
    }

    /** The alerts of the user, newest first. */
    public OperationResult<List<Alert>> list(boolean onlyUnread) {
        return run(user -> alertService.list(user, onlyUnread));
    }

    /** Marks one alert of the user as read. */
    public OperationResult<Alert> markAsRead(long alertId) {
        return run(user -> alertService.markAsRead(alertId, user));
    }

    /** Checks the session and converts errors into messages. */
    private <T> OperationResult<T> run(
            Function<UserSession, T> work) {

        if (!session.isActive()) {
            return OperationResult.error(
                    ErrorKind.AUTHENTICATION,
                    Messages.SESSION_REQUIRED);
        }

        try {
            return OperationResult.ok(
                    work.apply(session.current()));

        } catch (RuntimeException e) {

            OperationResult<T> failure =
                    OperationResult.failure(e);

            if (failure.error() == ErrorKind.UNEXPECTED
                    || failure.error() == ErrorKind.UNAVAILABLE) {

                LOG.log(
                        Level.WARNING,
                        "Alert operation failed",
                        e);
            }

            return failure;
        }
    }
}
