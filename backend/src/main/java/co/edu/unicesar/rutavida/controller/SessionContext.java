
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.Objects;

/**
 * Stores the current authenticated user session.
 * Shared by controllers that require authentication.
 */
public final class SessionContext {

    private UserSession current;

    public SessionContext() {
    }

    /**
     * Starts a user session.
     */
    public synchronized void start(UserSession session) {
        this.current = Objects.requireNonNull(session, "session");
    }

    /**
     * Returns the current session or rejects unauthenticated access.
     */
    public synchronized UserSession current() {
        if (current == null) {
            throw new AccessDeniedException(
                    Messages.get(Messages.SESSION_REQUIRED));
        }

        return current;
    }

    /**
     * Ends the current session.
     */
    public synchronized void end() {
        current = null;
    }

    /**
     * Returns true when a session is active.
     */
    public synchronized boolean isActive() {
        return current != null;
    }
}
