
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserSessionTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 15, 10, 0);

    private User createUser(RecordStatus status) {
        return new User(
                1, 2, "AB12345",
                "Sherly", "Perez", "sherly@example.com",
                "sherly_01", "hashed-value",
                status, NOW);
    }

    private Role createRole(RecordStatus status) {
        Permission consult = new Permission(
                1, "EMERGENCIAS", "CONSULTAR", null);

        return new Role(
                2, "OPERADOR", null,
                status, Set.of(consult));
    }

    @Test
    void createsAValidSession() {
        User user = createUser(RecordStatus.ACTIVE);
        Role role = createRole(RecordStatus.ACTIVE);

        UserSession session = new UserSession(user, role, NOW);

        assertEquals(user, session.user());
        assertEquals(role, session.role());
        assertEquals(NOW, session.startedAt());
    }

    @Test
    void rejectsNullRequiredFields() {
        User user = createUser(RecordStatus.ACTIVE);
        Role role = createRole(RecordStatus.ACTIVE);

        assertThrows(ValidationException.class,
                () -> new UserSession(null, role, NOW));

        assertThrows(ValidationException.class,
                () -> new UserSession(user, null, NOW));

        assertThrows(ValidationException.class,
                () -> new UserSession(user, role, null));
    }

    @Test
    void activeUserAndRoleCanConsult() {
        UserSession session = new UserSession(
                createUser(RecordStatus.ACTIVE),
                createRole(RecordStatus.ACTIVE), NOW);

        assertTrue(session.hasPermission(
                "EMERGENCIAS", "CONSULTAR"));

        assertDoesNotThrow(() ->
                session.require("EMERGENCIAS", "CONSULTAR"));
    }

    @Test
    void missingPermissionIsDenied() {
        UserSession session = new UserSession(
                createUser(RecordStatus.ACTIVE),
                createRole(RecordStatus.ACTIVE), NOW);

        assertFalse(session.hasPermission(
                "EMERGENCIAS", "GESTIONAR"));

        assertThrows(AccessDeniedException.class, () ->
                session.require("EMERGENCIAS", "GESTIONAR"));
    }

    @Test
    void inactiveUserCannotUsePermissions() {
        UserSession session = new UserSession(
                createUser(RecordStatus.INACTIVE),
                createRole(RecordStatus.ACTIVE), NOW);

        assertFalse(session.hasPermission(
                "EMERGENCIAS", "CONSULTAR"));

        assertThrows(AccessDeniedException.class, () ->
                session.require("EMERGENCIAS", "CONSULTAR"));
    }

    @Test
    void inactiveRoleCannotGrantPermissions() {
        UserSession session = new UserSession(
                createUser(RecordStatus.ACTIVE),
                createRole(RecordStatus.INACTIVE), NOW);

        assertFalse(session.hasPermission(
                "EMERGENCIAS", "CONSULTAR"));

        assertThrows(AccessDeniedException.class, () ->
                session.require("EMERGENCIAS", "CONSULTAR"));
    }


    @Test
    void rejectsSessionWithDifferentRole() {
        User user = createUser(RecordStatus.ACTIVE);

        Role differentRole = new Role(
                3, "SUPERVISOR", null,
                RecordStatus.ACTIVE, Set.of());

        assertThrows(ValidationException.class, () ->
                new UserSession(user, differentRole, NOW));
    }

}
