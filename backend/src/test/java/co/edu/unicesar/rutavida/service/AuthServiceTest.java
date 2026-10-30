
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T15:00:00Z"),
            ZoneId.of("America/Bogota"));

    private PasswordHasher hasher;
    private InMemoryUserDAO userDao;
    private InMemoryRoleDAO roleDao;
    private InMemoryAuditDAO auditDao;
    private AuthService authService;


    @BeforeEach
    void setUp() {
        hasher = new PasswordHasher(1_000);

        userDao = new InMemoryUserDAO();
        roleDao = new InMemoryRoleDAO();
        auditDao = new InMemoryAuditDAO();

        AuditService auditService =
                new AuditService(auditDao, CLOCK);

        authService = new AuthService(
                userDao,
                roleDao,
                hasher,
                auditService,
                CLOCK
        );
    }


    private User createUser(RecordStatus status) {

        Permission permission = new Permission(
                1,
                "DESPACHO",
                "GESTIONAR",
                "Permiso para gestionar despachos"
        );

        Role role = new Role(
                3,
                "COORDINADOR",
                null,
                RecordStatus.ACTIVE,
                Set.of(permission)
        );

        roleDao.add(role);

        String passwordHash = hasher.hash(
                "Clave-Segura1".toCharArray());

        User user = new User(
                0,
                3,
                "1234567890",
                "Ana",
                "Pérez",
                "ana.perez@example.com",
                "ana.perez",
                passwordHash,
                status,
                LocalDateTime.now(CLOCK)
        );

        userDao.insert(user);

        return user;
    }

    @Test
    void validLoginCreatesSessionWithRoleAndPermissions() {

        User user = createUser(RecordStatus.ACTIVE);

        UserSession session = authService.login(
                "ana.perez",
                "Clave-Segura1".toCharArray()
        );

        assertEquals(user.getId(), session.user().getId());
        assertEquals("COORDINADOR", session.role().getName());

        assertTrue(authService.hasPermission(
                session, "DESPACHO", "GESTIONAR"));

        assertEquals(
                LocalDateTime.of(2026, 10, 17, 10, 0),
                session.startedAt()
        );

        assertEquals(1, auditDao.saved.size());

        AuditLog log = auditDao.last();
        assertEquals(AuditResult.SUCCESS, log.getResult());
        assertEquals("LOGIN", log.getOperation());
    }

    @Test
    void wrongCredentialsHaveTheSameErrorMessage() {

        createUser(RecordStatus.ACTIVE);

        BusinessRuleException unknownUser = assertThrows(
                BusinessRuleException.class,
                () -> authService.login(
                        "usuario.inexistente",
                        "Clave-Segura1".toCharArray())
        );

        BusinessRuleException wrongPassword = assertThrows(
                BusinessRuleException.class,
                () -> authService.login(
                        "ana.perez",
                        "Clave-Incorrecta".toCharArray())
        );

        assertEquals(
                unknownUser.getMessage(),
                wrongPassword.getMessage()
        );

        assertEquals(
                "Usuario o contraseña incorrectos",
                unknownUser.getMessage()
        );
    }

    @Test
    void failedLoginAttemptsAreAudited() {

        User user = createUser(RecordStatus.ACTIVE);

        assertThrows(BusinessRuleException.class, () ->
                authService.login(
                        "usuario.inexistente",
                        "Clave-Segura1".toCharArray()
                )
        );

        assertThrows(BusinessRuleException.class, () ->
                authService.login(
                        "ana.perez",
                        "Clave-Incorrecta".toCharArray()
                )
        );

        assertEquals(2, auditDao.saved.size());

        AuditLog first = auditDao.saved.get(0);
        AuditLog second = auditDao.saved.get(1);

        assertEquals(AuditResult.FAILURE, first.getResult());
        assertEquals(AuditResult.FAILURE, second.getResult());

        assertEquals("LOGIN", first.getOperation());
        assertEquals("LOGIN", second.getOperation());

        assertNull(first.getUserId());
        assertEquals(user.getId(), second.getUserId());
    }

    @Test
    void inactiveUserCannotLogin() {

        User user = createUser(RecordStatus.INACTIVE);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> authService.login(
                        "ana.perez",
                        "Clave-Segura1".toCharArray()
                )
        );

        assertEquals(
                "El usuario está inactivo",
                exception.getMessage()
        );

        assertEquals(1, auditDao.saved.size());

        AuditLog log = auditDao.last();

        assertEquals(user.getId(), log.getUserId());
        assertEquals("LOGIN", log.getOperation());
        assertEquals(AuditResult.FAILURE, log.getResult());
    }

    @Test
    void inactiveUserWithWrongPasswordGetsGenericMessage() {

        createUser(RecordStatus.INACTIVE);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> authService.login(
                        "ana.perez",
                        "Clave-Incorrecta".toCharArray()
                )
        );

        assertEquals(
                "Usuario o contraseña incorrectos",
                exception.getMessage()
        );

        assertEquals(1, auditDao.saved.size());

        AuditLog log = auditDao.last();

        assertEquals("LOGIN", log.getOperation());
        assertEquals(AuditResult.FAILURE, log.getResult());
    }

    @Test
    void emptyCredentialsAreRejectedWithoutAudit() {

        assertThrows(ValidationException.class, () ->
                authService.login(null, "Clave-Segura1".toCharArray())
        );

        assertThrows(ValidationException.class, () ->
                authService.login("", "Clave-Segura1".toCharArray())
        );

        assertThrows(ValidationException.class, () ->
                authService.login("   ", "Clave-Segura1".toCharArray())
        );

        assertThrows(ValidationException.class, () ->
                authService.login("ana.perez", null)
        );

        assertThrows(ValidationException.class, () ->
                authService.login("ana.perez", new char[0])
        );

        assertTrue(auditDao.saved.isEmpty());
    }

    @Test
    void logoutRecordsSuccessfulAudit() {

        User user = createUser(RecordStatus.ACTIVE);

        UserSession session = authService.login(
                "ana.perez",
                "Clave-Segura1".toCharArray()
        );

        authService.logout(session);

        assertEquals(2, auditDao.saved.size());

        AuditLog log = auditDao.last();

        assertEquals(user.getId(), log.getUserId());
        assertEquals("SEGURIDAD", log.getModule());
        assertEquals("LOGOUT", log.getOperation());
        assertEquals(AuditResult.SUCCESS, log.getResult());
    }

    @Test
    void theUsernameIsNormalizedBeforeSearching() {
        createUser(RecordStatus.ACTIVE);
        UserSession session = authService.login("  ANA.Perez ", "Clave-Segura1".toCharArray());
        assertEquals("ana.perez", session.user().getUsername());
    }

    @Test
    void permissionsComeFromAnActiveRoleOfAnActiveUser() {
        User user = createUser(RecordStatus.ACTIVE);
        Role role = roleDao.findById(3).orElseThrow();
        UserSession session = new UserSession(user, role, LocalDateTime.now(CLOCK));

        assertTrue(authService.hasPermission(session, "DESPACHO", "GESTIONAR"));
        assertTrue(authService.hasPermission(session, "despacho", "gestionar"));
        assertFalse(authService.hasPermission(session, "USUARIOS", "GESTIONAR"));
        assertThrows(AccessDeniedException.class,
                () -> authService.requirePermission(session, "USUARIOS", "GESTIONAR"));

        User inactive = new User(9, 3, "1234567899", "Inés", "Activa", "ines@example.com", "ines",
                "HASH", RecordStatus.INACTIVE, LocalDateTime.now(CLOCK));
        assertFalse(authService.hasPermission(new UserSession(inactive, role, LocalDateTime.now(CLOCK)),
                "DESPACHO", "GESTIONAR"));

        Role inactiveRole = new Role(3, "COORDINADOR", null, RecordStatus.INACTIVE, role.getPermissions());
        assertFalse(authService.hasPermission(new UserSession(user, inactiveRole, LocalDateTime.now(CLOCK)),
                "DESPACHO", "GESTIONAR"));
    }
}
