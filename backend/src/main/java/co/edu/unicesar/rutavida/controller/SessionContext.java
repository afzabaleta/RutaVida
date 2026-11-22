package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.exception.SessionRequiredException;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.util.Messages;

import java.util.Objects;

/**
 * Stores the current authenticated user session.
 * Shared by controllers that require authentication.
 *
 * <p>Two ways to have a session:
 * <ul>
 *   <li>{@link #start}: one session for the whole application (the console tools, a
 *       desktop view). It is the same for every thread.</li>
 *   <li>{@link #runAs}: the session of one web request, only for the thread that serves
 *       it. The web server has many users at once, so each request runs with its own
 *       session and never touches the shared one.</li>
 * </ul>
 * {@link #current()} returns the request session when there is one, otherwise the shared
 * one. The controllers do not need to know which of the two they got. The audit triggers
 * also get the right user, because AppContext reads the user id from here when it opens a
 * connection.
 */
public final class SessionContext {

    /** Work done on behalf of a web request; it may throw what the request handler throws. */
    @FunctionalInterface
    public interface SessionWork {
        void run() throws Exception;
    }

    private final ThreadLocal<UserSession> requestSession = new ThreadLocal<>();
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
        UserSession ofThisRequest = requestSession.get();
        if (ofThisRequest != null) {
            return ofThisRequest;
        }
        if (current == null) {
            throw new SessionRequiredException(
                    Messages.SESSION_REQUIRED);
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
        return requestSession.get() != null || current != null;
    }

    /**
     * Runs {@code work} with {@code session} as the current session of this thread only,
     * and removes it at the end, even if the work fails. Used by the web server for each
     * request.
     */
    public void runAs(UserSession session, SessionWork work) throws Exception {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(work, "work");
        UserSession previous = requestSession.get();
        requestSession.set(session);
        try {
            work.run();
        } finally {
            if (previous == null) {
                requestSession.remove();
            } else {
                requestSession.set(previous);
            }
        }
    }
}