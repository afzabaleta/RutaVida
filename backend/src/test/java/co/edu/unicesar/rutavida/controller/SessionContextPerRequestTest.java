package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.SessionRequiredException;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The session of a web request (SessionContext.runAs) only exists in its own thread. */
class SessionContextPerRequestTest {

    private static UserSession session(long id, String username) {
        Role role = new Role(1L, "OPERADOR_CENTRAL", "Operador", RecordStatus.ACTIVE, Set.of());
        User user = new User(id, 1L, "DOC" + id, "Nombre", "Apellido", username + "@ejemplo.com",
                username, "NO_ES_UN_HASH", RecordStatus.ACTIVE, LocalDateTime.of(2026, 11, 5, 9, 0));
        return new UserSession(user, role, LocalDateTime.of(2026, 11, 5, 9, 0));
    }

    @Test
    void withoutSessionTheErrorSaysToLogIn() {
        SessionContext context = new SessionContext();

        SessionRequiredException error = assertThrows(SessionRequiredException.class, context::current);
        assertTrue(error instanceof AccessDeniedException, "old code that catches AccessDeniedException still works");
    }

    @Test
    void runAsWinsOverTheSharedSessionAndIsRemovedAfterwards() throws Exception {
        SessionContext context = new SessionContext();
        UserSession shared = session(1, "consola");
        UserSession request = session(2, "ana.perez");
        context.start(shared);
        AtomicReference<UserSession> inside = new AtomicReference<>();

        context.runAs(request, () -> inside.set(context.current()));

        assertSame(request, inside.get());
        assertSame(shared, context.current(), "the shared session is untouched");
    }

    @Test
    void theSessionIsRemovedEvenWhenTheRequestFails() {
        SessionContext context = new SessionContext();

        assertThrows(IllegalStateException.class, () -> context.runAs(session(2, "ana.perez"), () -> {
            throw new IllegalStateException("falla de prueba");
        }));

        assertFalse(context.isActive());
    }

    @Test
    void twoRequestsAtTheSameTimeSeeTheirOwnUser() throws Exception {
        SessionContext context = new SessionContext();
        UserSession ana = session(2, "ana.perez");
        UserSession luis = session(3, "luis.gomez");
        CountDownLatch bothInside = new CountDownLatch(2);
        AtomicReference<UserSession> seenByAna = new AtomicReference<>();
        AtomicReference<UserSession> seenByLuis = new AtomicReference<>();

        Thread first = new Thread(() -> runQuietly(context, ana, bothInside, seenByAna));
        Thread second = new Thread(() -> runQuietly(context, luis, bothInside, seenByLuis));
        first.start();
        second.start();
        first.join(5_000);
        second.join(5_000);

        assertSame(ana, seenByAna.get());
        assertSame(luis, seenByLuis.get());
        assertFalse(context.isActive(), "this thread never had a session");
    }

    private static void runQuietly(SessionContext context, UserSession session, CountDownLatch bothInside,
                                   AtomicReference<UserSession> seen) {
        try {
            context.runAs(session, () -> {
                bothInside.countDown();
                bothInside.await(5, TimeUnit.SECONDS);
                seen.set(context.current());
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}