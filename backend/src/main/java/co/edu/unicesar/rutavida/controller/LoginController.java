
package co.edu.unicesar.rutavida.controller;


import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.Arrays;
import java.util.logging.Level;

import co.edu.unicesar.rutavida.service.AuthService;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Controller responsible for user login and logout.
 * Delegates authentication to AuthService.
 */
public class LoginController {

    private static final Logger LOG =
            Logger.getLogger(LoginController.class.getName());

    private final AuthService authService;
    private final SessionContext session;

    public LoginController(
            AuthService authService,
            SessionContext session) {

        this.authService =
                Objects.requireNonNull(authService, "authService");

        this.session =
                Objects.requireNonNull(session, "session");
    }

    /**
     * Authenticates a user and starts a session.
     * Returns a result instead of exposing exceptions to the view.
     */
    public OperationResult<UserSession> login(
            String username,
            char[] password) {

        try {
            UserSession userSession =
                    authService.login(username, password);

            session.start(userSession);

            return OperationResult.ok(userSession);

        } catch (ValidationException | BusinessRuleException e) {

            return OperationResult.error(e.getMessage());

        } catch (RuntimeException e) {

            LOG.log(Level.WARNING, "Login failed due to a technical error", e);

            return OperationResult.error(
                    Messages.userMessage(e));

        } finally {

            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * Ends the current user session.
     * The local session is closed even if audit logging fails.
     */
    public void logout() {

        if (!session.isActive()) {
            return;
        }

        UserSession currentSession = session.current();

        session.end();

        try {
            authService.logout(currentSession);

        } catch (RuntimeException e) {
            LOG.log(
                    Level.WARNING,
                    "Logout audit failed",
                    e
            );
        }
    }

}
