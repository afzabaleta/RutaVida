
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.UserRequest;
import co.edu.unicesar.rutavida.dto.UserView;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.UserService;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User management for the view (CU-03). It checks the session and the permission,
 * delegates to UserService and converts errors into messages in Spanish with their kind.
 * Users leave this class as UserView: the password hash never reaches the view.
 */
public class UserController {

    private static final Logger LOG = Logger.getLogger(UserController.class.getName());

    static final String MODULE = "USUARIOS";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final UserService userService;
    private final AuthService authService;
    private final SessionContext session;

    public UserController(UserService userService, AuthService authService,
                          SessionContext session) {
        this.userService = Objects.requireNonNull(userService, "userService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** CU-03: creates a user. */
    public OperationResult<UserView> create(UserRequest request) {
        return run(MANAGE, user -> UserView.of(userService.create(request, user)));
    }

    /** CU-03: every user, by username. */
    public OperationResult<List<UserView>> list() {
        return run(VIEW, user -> userService.listAll().stream()
                .map(UserView::of)
                .toList());
    }

    /** CU-03: the user can no longer log in. */
    public OperationResult<UserView> deactivate(long userId) {
        return run(MANAGE,
                user -> UserView.of(userService.deactivate(userId, user)));
    }

    /** CU-03: an inactive user can log in again. */
    public OperationResult<UserView> activate(long userId) {
        return run(MANAGE,
                user -> UserView.of(userService.activate(userId, user)));
    }

    /** Any logged-in user changes his own password: no permission needed, only the session. */
    public OperationResult<Void> changeOwnPassword(char[] current, char[] next) {
        return run(null, user -> {
            userService.changeOwnPassword(user, current, next);
            return null;
        });
    }

    /** Checks the session and, if {@code action} is not null, the permission. */
    private <T> OperationResult<T> run(String action, Function<UserSession, T> work) {
        if (!session.isActive()) {
            return OperationResult.error(
                    ErrorKind.AUTHENTICATION,
                    Messages.SESSION_REQUIRED
            );
        }

        try {
            UserSession user = session.current();

            if (action != null) {
                authService.requirePermission(user, MODULE, action);
            }

            return OperationResult.ok(work.apply(user));

        } catch (RuntimeException e) {
            OperationResult<T> failure = OperationResult.failure(e);

            if (failure.error() == ErrorKind.UNEXPECTED
                    || failure.error() == ErrorKind.UNAVAILABLE) {
                LOG.log(Level.WARNING, "User operation failed", e);
            }

            return failure;
        }
    }
}
