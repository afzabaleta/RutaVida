
package co.edu.unicesar.rutavida.controller;


import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SessionContextTest {

    @Test
    void newContextHasNoActiveSession() {

        SessionContext context = new SessionContext();

        assertFalse(context.isActive());

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                context::current
        );

        assertEquals(
                "Debe iniciar sesión.",
                exception.getMessage()
        );
    }

    @Test
    void startAndEndManageTheSession() {

        Role role = new Role(
                3L,
                "COORDINADOR",
                "Coordinador de emergencias",
                RecordStatus.ACTIVE,
                Set.of()
        );

        User user = new User(
                7L,
                3L,
                "1234567890",
                "Ana",
                "Pérez",
                "ana.perez@example.com",
                "ana.perez",
                "hash-de-prueba",
                RecordStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );

        UserSession session = new UserSession(
                user,
                role,
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );

        SessionContext context = new SessionContext();

        context.start(session);

        assertTrue(context.isActive());
        assertSame(session, context.current());

        context.end();

        assertFalse(context.isActive());

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                context::current
        );

        assertEquals(
                "Debe iniciar sesión.",
                exception.getMessage()
        );
    }

    @Test
    void startRejectsNullSession() {

        SessionContext context = new SessionContext();

        assertThrows(
                NullPointerException.class,
                () -> context.start(null)
        );
    }

}
