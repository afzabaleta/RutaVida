package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AdminBootstrapServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-22T14:00:00Z"), ZoneId.of("America/Bogota"));

    /** Runs the work directly: there is no database in these tests. */
    private static final TransactionManager NO_DATABASE = new TransactionManager() {
        @Override
        public <T> T callInTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private PasswordHasher hasher;
    private InMemoryUserDAO userDao;
    private InMemoryRoleDAO roleDao;
    private InMemoryAuditDAO auditDao;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        hasher = new PasswordHasher(1_000);
        userDao = new InMemoryUserDAO();
        roleDao = new InMemoryRoleDAO();
        auditDao = new InMemoryAuditDAO();
        roleDao.add(new Role(1, "ADMINISTRADOR", null, RecordStatus.ACTIVE, Set.of()));
        roleDao.add(new Role(3, "COORDINADOR", null, RecordStatus.ACTIVE, Set.of()));
        service = new AdminBootstrapService(userDao, roleDao, hasher,
                new AuditService(auditDao, CLOCK), NO_DATABASE, CLOCK);
    }

    private User createDefaultAdmin() {
        return service.createAdmin("1065000001", "Andrés", "Prueba", "Admin@Ejemplo.com",
                "Admin.RutaVida", "Clave-Segura1".toCharArray());
    }

    @Test
    void createsAnActiveAdministratorWithAHashedPasswordAndAnAudit() {
        User admin = createDefaultAdmin();

        assertTrue(admin.getId() > 0);
        assertEquals(1, admin.getRoleId());
        assertEquals("admin.rutavida", admin.getUsername());
        assertEquals("admin@ejemplo.com", admin.getEmail());
        assertEquals(RecordStatus.ACTIVE, admin.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 0), admin.getRegisteredAt());
        assertNotEquals("Clave-Segura1", admin.getPasswordHash());
        assertTrue(hasher.matches("Clave-Segura1".toCharArray(), admin.getPasswordHash()));

        AuditLog log = auditDao.last();
        assertEquals(admin.getId(), log.getUserId());
        assertEquals("SEGURIDAD", log.getModule());
        assertEquals("CREAR_ADMIN", log.getOperation());
        assertEquals(AuditResult.SUCCESS, log.getResult());
    }

    @Test
    void rejectsWeakPasswordsBeforeSavingAnything() {
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", "Andrés",
                "Prueba", "admin@ejemplo.com", "admin.rutavida", "Corta1".toCharArray()));
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", "Andrés",
                "Prueba", "admin@ejemplo.com", "admin.rutavida", "SoloLetrasLargas".toCharArray()));
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", "Andrés",
                "Prueba", "admin@ejemplo.com", "admin.rutavida", "1234567890".toCharArray()));
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", "Andrés",
                "Prueba", "admin@ejemplo.com", "admin.rutavida", null));

        assertTrue(userDao.findAll().isEmpty());
        assertTrue(auditDao.saved.isEmpty());
    }

    @Test
    void aTakenUsernameIsRejected() {
        createDefaultAdmin();

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> service.createAdmin(
                "1065000002", "Otra", "Persona", "otra@ejemplo.com", "ADMIN.rutavida",
                "Otra-Clave22".toCharArray()));

        assertEquals("Ya existe un usuario con ese nombre de usuario.", error.getMessage());
        assertEquals(1, userDao.findAll().size());
    }

    @Test
    void invalidPersonalDataIsRejectedByTheService() {
        assertThrows(ValidationException.class, () -> service.createAdmin("12", "Andrés", "Prueba",
                "admin@ejemplo.com", "admin.rutavida", "Clave-Segura1".toCharArray()));
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", "Andrés", "Prueba",
                "no-es-un-correo", "admin.rutavida", "Clave-Segura1".toCharArray()));
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", "Andrés", "Prueba",
                "admin@ejemplo.com", "ab", "Clave-Segura1".toCharArray()));
        assertThrows(ValidationException.class, () -> service.createAdmin("1065000001", " ", "Prueba",
                "admin@ejemplo.com", "admin.rutavida", "Clave-Segura1".toCharArray()));

        assertTrue(userDao.findAll().isEmpty());
    }

    @Test
    void countsOnlyTheAdministrators() {
        assertEquals(0, service.countAdmins());

        createDefaultAdmin();
        userDao.insert(new User(0, 3, "1065000009", "Coordinador", "Prueba", "coord@ejemplo.com",
                "coordinador", "HASH", RecordStatus.ACTIVE, LocalDateTime.now(CLOCK)));

        assertEquals(1, service.countAdmins());
    }

    @Test
    void withoutTheAdministratorRoleNothingCanBeCreated() {
        InMemoryRoleDAO noRoles = new InMemoryRoleDAO();
        AdminBootstrapService withoutRole = new AdminBootstrapService(userDao, noRoles, hasher,
                new AuditService(auditDao, CLOCK), NO_DATABASE, CLOCK);

        assertThrows(NotFoundException.class, withoutRole::countAdmins);

        InMemoryRoleDAO inactive = new InMemoryRoleDAO();
        inactive.add(new Role(1, "ADMINISTRADOR", null, RecordStatus.INACTIVE, Set.of()));
        AdminBootstrapService withInactiveRole = new AdminBootstrapService(userDao, inactive, hasher,
                new AuditService(auditDao, CLOCK), NO_DATABASE, CLOCK);

        assertThrows(BusinessRuleException.class, () -> withInactiveRole.createAdmin("1065000001",
                "Andrés", "Prueba", "admin@ejemplo.com", "admin.rutavida", "Clave-Segura1".toCharArray()));
    }
}