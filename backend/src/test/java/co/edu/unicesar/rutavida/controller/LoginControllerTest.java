
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginControllerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T15:00:00Z"),
            ZoneId.of("America/Bogota")
    );

    private PasswordHasher hasher;
    private InMemoryUserDAO userDao;
    private InMemoryRoleDAO roleDao;
    private InMemoryAuditDAO auditDao;
    private SessionContext session;
    private LoginController controller;

    @BeforeEach
    void setUp() {

        hasher = new PasswordHasher(1_000);

        userDao = new InMemoryUserDAO();
        roleDao = new InMemoryRoleDAO();
        auditDao = new InMemoryAuditDAO();

        session = new SessionContext();

        AuditService auditService =
                new AuditService(auditDao, CLOCK);

        AuthService authService = new AuthService(
                userDao,
                roleDao,
                hasher,
                auditService,
                CLOCK
        );

        controller = new LoginController(
                authService,
                session
        );
    }

    private User createUser() {

        Role role = new Role(
                3L,
                "COORDINADOR",
                "Coordinador de emergencias",
                RecordStatus.ACTIVE,
                Set.of()
        );

        roleDao.add(role);

        String passwordHash = hasher.hash(
                "Clave-Segura1".toCharArray()
        );

        User user = new User(
                0L,
                3L,
                "1234567890",
                "Ana",
                "Pérez",
                "ana.perez@example.com",
                "ana.perez",
                passwordHash,
                RecordStatus.ACTIVE,
                LocalDateTime.now(CLOCK)
        );

        userDao.insert(user);

        return user;
    }

    @Test
    void successfulLoginStartsSession() {

        User user = createUser();

        char[] password = "Clave-Segura1".toCharArray();

        OperationResult<UserSession> result =
                controller.login("ana.perez", password);

        assertTrue(result.success());
        assertNull(result.message());
        assertNotNull(result.value());

        assertTrue(session.isActive());
        assertSame(result.value(), session.current());

        assertEquals(
                user.getId(),
                result.value().user().getId()
        );

        assertArrayEquals(
                new char[password.length],
                password
        );
    }

    @Test
    void wrongPasswordDoesNotStartSession() {

        createUser();

        char[] password = "Clave-Incorrecta".toCharArray();

        OperationResult<UserSession> result =
                controller.login("ana.perez", password);

        assertFalse(result.success());

        assertEquals(
                "Usuario o contraseña incorrectos",
                result.message()
        );

        assertNull(result.value());

        assertFalse(session.isActive());

        assertArrayEquals(
                new char[password.length],
                password
        );
    }

    @Test
    void emptyUsernameDoesNotStartSession() {

        createUser();

        char[] password = "Clave-Segura1".toCharArray();

        OperationResult<UserSession> result =
                controller.login("   ", password);

        assertFalse(result.success());

        assertNotNull(result.message());
        assertFalse(result.message().isBlank());

        assertNull(result.value());
        assertFalse(session.isActive());

        assertArrayEquals(
                new char[password.length],
                password
        );
    }

    private LoginController createControllerWithDatabaseFailure() {

        InMemoryUserDAO failingUserDao = new InMemoryUserDAO() {

            @Override
            public Optional<User> findByUsername(String username) {
                throw new DataAccessException(
                        "ORA-12541: TNS:no listener",
                        null
                );
            }
        };

        AuditService auditService =
                new AuditService(auditDao, CLOCK);

        AuthService authService = new AuthService(
                failingUserDao,
                roleDao,
                hasher,
                auditService,
                CLOCK
        );

        return new LoginController(
                authService,
                session
        );
    }

    @Test
    void databaseFailureShowsSafeMessage() {

        LoginController failingController =
                createControllerWithDatabaseFailure();

        char[] password = "Clave-Segura1".toCharArray();

        OperationResult<UserSession> result =
                failingController.login("ana.perez", password);

        assertFalse(result.success());

        assertEquals(
                "No hay conexión con la base de datos.",
                result.message()
        );

        assertFalse(result.message().contains("ORA"));

        assertNull(result.value());
        assertFalse(session.isActive());

        assertArrayEquals(
                new char[password.length],
                password
        );
    }

    @Test
    void emptyPasswordDoesNotStartSession() {

        createUser();

        char[] password = new char[0];

        OperationResult<UserSession> result =
                controller.login("ana.perez", password);

        assertFalse(result.success());

        assertNotNull(result.message());
        assertFalse(result.message().isBlank());

        assertNull(result.value());
        assertFalse(session.isActive());

        assertEquals(0, password.length);
    }

    @Test
    void logoutClosesSessionAndRecordsAudit() {

        createUser();

        OperationResult<UserSession> result =
                controller.login(
                        "ana.perez",
                        "Clave-Segura1".toCharArray()
                );

        assertTrue(result.success());
        assertTrue(session.isActive());

        int auditCountBeforeLogout = auditDao.saved.size();

        controller.logout();

        assertFalse(session.isActive());

        assertEquals(
                auditCountBeforeLogout + 1,
                auditDao.saved.size()
        );

        AuditLog lastAudit = auditDao.last();

        assertEquals(
                "LOGOUT",
                lastAudit.getOperation()
        );

        assertEquals(
                AuditResult.SUCCESS,
                lastAudit.getResult()
        );
    }

    @Test
    void logoutWithoutActiveSessionDoesNothing() {

        assertFalse(session.isActive());

        int auditCountBefore = auditDao.saved.size();

        assertDoesNotThrow(
                () -> controller.logout()
        );

        assertFalse(session.isActive());

        assertEquals(
                auditCountBefore,
                auditDao.saved.size()
        );
    }

}
