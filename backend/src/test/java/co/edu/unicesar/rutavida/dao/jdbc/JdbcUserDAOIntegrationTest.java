package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JdbcUserDAO against the local Oracle. Skipped without Oracle; every write is rolled back. */
class JdbcUserDAOIntegrationTest {

    private static JdbcTransactionManager jdbc;
    private static JdbcUserDAO dao;
    private static long coordinatorRole;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        dao = new JdbcUserDAO(jdbc);
        coordinatorRole = number(jdbc, "SELECT id_rol FROM rol WHERE nombre_rol = ?", "COORDINADOR");
    }

    private static User newUser(String username, String document, LocalDateTime registeredAt) {
        return new User(0, coordinatorRole, document, "Prueba", "JUnit",
                username + "@ejemplo.com", username, "NO_ES_UN_HASH", RecordStatus.ACTIVE, registeredAt);
    }

    @Test
    void insertAssignsTheIdAndTheUserCanBeReadBack() {
        LocalDateTime registeredAt = LocalDateTime.now().withNano(0);
        inRolledBackTransaction(jdbc, () -> {
            User user = newUser("prueba_junit_dao", "JUNIT0001", registeredAt);
            long id = dao.insert(user);
            assertTrue(id > 0);
            assertEquals(id, user.getId());

            User saved = dao.findById(id).orElseThrow();
            assertEquals(coordinatorRole, saved.getRoleId());
            assertEquals("JUNIT0001", saved.getDocument());
            assertEquals("prueba_junit_dao@ejemplo.com", saved.getEmail());
            assertEquals("prueba_junit_dao", saved.getUsername());
            assertEquals("NO_ES_UN_HASH", saved.getPasswordHash());
            assertEquals(RecordStatus.ACTIVE, saved.getStatus());
            assertEquals(registeredAt, saved.getRegisteredAt());

            assertEquals(id, dao.findByUsername("  PRUEBA_JUNIT_DAO ").orElseThrow().getId());
            assertTrue(dao.findAll().stream().anyMatch(u -> u.getId() == id));
        });
        assertTrue(dao.findByUsername("prueba_junit_dao").isEmpty());
    }

    @Test
    void updateChangesTheDataButKeepsTheRegistrationDate() {
        LocalDateTime registeredAt = LocalDateTime.now().withNano(0);
        inRolledBackTransaction(jdbc, () -> {
            User user = newUser("prueba_junit_upd", "JUNIT0002", registeredAt);
            long id = dao.insert(user);

            dao.update(new User(id, coordinatorRole, user.getDocument(), "Otro", "Nombre",
                    "otro.correo@ejemplo.com", "prueba_junit_upd", "OTRO_HASH",
                    RecordStatus.INACTIVE, registeredAt.plusDays(5)));

            User saved = dao.findById(id).orElseThrow();
            assertEquals("Otro", saved.getFirstNames());
            assertEquals("otro.correo@ejemplo.com", saved.getEmail());
            assertEquals("OTRO_HASH", saved.getPasswordHash());
            assertEquals(RecordStatus.INACTIVE, saved.getStatus());
            assertEquals(registeredAt, saved.getRegisteredAt());
        });
    }

    @Test
    void repeatedUsernameIsRejectedByOracle() {
        LocalDateTime registeredAt = LocalDateTime.now().withNano(0);
        inRolledBackTransaction(jdbc, () -> {
            dao.insert(newUser("prueba_junit_rep", "JUNIT0003", registeredAt));
            User copy = new User(0, coordinatorRole, "JUNIT0004", "Prueba", "JUnit",
                    "otro.junit@ejemplo.com", "prueba_junit_rep", "NO_ES_UN_HASH",
                    RecordStatus.ACTIVE, registeredAt);
            assertThrows(DataAccessException.class, () -> dao.insert(copy));
        });
    }

    @Test
    void missingUsersAreEmptyOrNotFound() {
        assertTrue(dao.findById(-1).isEmpty());
        assertTrue(dao.findByUsername("no_existe_junit").isEmpty());
        assertTrue(dao.findByUsername("   ").isEmpty());
        assertTrue(dao.findByUsername(null).isEmpty());

        User missing = new User(999_999_999L, coordinatorRole, "JUNIT9999", "Prueba", "JUnit",
                "no.existe@ejemplo.com", "no_existe_junit", "NO_ES_UN_HASH",
                RecordStatus.ACTIVE, LocalDateTime.now());
        assertThrows(NotFoundException.class, () -> dao.update(missing));
    }

    @Test
    void insertAndUpdateRejectTheWrongKindOfUser() {
        User saved = new User(5, coordinatorRole, "JUNIT5555", "Prueba", "JUnit",
                "ya.guardado@ejemplo.com", "ya_guardado", "NO_ES_UN_HASH",
                RecordStatus.ACTIVE, LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> dao.insert(saved));
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(newUser("sin_guardar", "JUNIT0005", LocalDateTime.now())));
    }
}