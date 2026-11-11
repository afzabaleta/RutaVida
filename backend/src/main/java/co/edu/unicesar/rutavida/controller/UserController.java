
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

public class UserController {

    private static final Logger LOG =
            Logger.getLogger(UserController.class.getName());

    static final String MODULE = "USUARIOS";
    static final String MANAGE = "GESTIONAR";
    static final String VIEW = "CONSULTAR";

    private final UserService userService;
    private final AuthService authService;
    private final SessionContext session;

    public UserController(
            UserService userService,
            AuthService authService,
            SessionContext session) {

        this.userService = Objects.requireNonNull(userService, "userService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.session = Objects.requireNonNull(session, "session");
    }

    public OperationResult<UserView> create(UserRequest request) {
        return run(MANAGE,
                user -> UserView.of(userService.create(request, user)));
    }

    public OperationResult<List<UserView>> list() {
        return run(VIEW,
                user -> userService.listAll().stream()
                        .map(UserView::of)
                        .toList());
    }

    public OperationResult<UserView> deactivate(long userId) {
        return run(MANAGE,
                user -> UserView.of(userService.deactivate(userId, user)));
    }

    public OperationResult<UserView> activate(long userId) {
        return run(MANAGE,
                user -> UserView.of(userService.activate(userId, user)));
    }

    public OperationResult<Void> changeOwnPassword(
            char[] current,
            char[] next) {

        return run(null, user -> {
            userService.changeOwnPassword(user, current, next);
            return null;
        });
    }

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
