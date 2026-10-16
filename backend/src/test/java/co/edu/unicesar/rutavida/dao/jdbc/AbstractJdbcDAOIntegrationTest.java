package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests of AbstractJdbcDAO against the local Oracle (schema RUTAVIDA with the
 * initial data of 02_datos_iniciales.sql). They are SKIPPED, not failed, when there is no
 * db.properties or Oracle is not running, so they never break a teammate's build.
 * Every write runs inside a transaction that is rolled back: the database is left unchanged.
 */
class AbstractJdbcDAOIntegrationTest {

    private static JdbcTransactionManager jdbc;
    private static ProbeDao dao;

    /** Minimal DAO used only to reach the protected methods. */
    private static final class ProbeDao extends AbstractJdbcDAO {
        ProbeDao(ConnectionProvider connections) {
            super(connections);
        }
    }

    /** Thrown on purpose to force a rollback at the end of a test transaction. */
    private static final class Rollback extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @BeforeAll
    static void connect() {
        assumeTrue(Files.exists(Path.of(DatabaseConnection.CONFIG_FILE)),
                "No db.properties: integration tests skipped");
        DatabaseConnection database = DatabaseConnection.fromEnvironment();
        jdbc = new JdbcTransactionManager(database::open);
        try {
            jdbc.withConnection(connection -> connection.isValid(5));
        } catch (DataAccessException e) {
            assumeTrue(false, "Oracle not available: integration tests skipped");
        }
        dao = new ProbeDao(jdbc);
    }

    @Test
    void queryListMapsEveryRowInOrder() {
        List<String> names = dao.queryList(
                "SELECT nombre FROM tipo_emergencia ORDER BY nombre", row -> row.getString("nombre"));
        assertEquals(List.of("ACCIDENTE_VIAL", "EMERGENCIA_MEDICA", "INCENDIO", "RESCATE"), names);
    }

    @Test
    void queryOneFindsARowByParameter() {
        Optional<Integer> priority = dao.queryOne(
                "SELECT prioridad FROM tipo_emergencia WHERE nombre = ?", row -> row.getInt("prioridad"), "INCENDIO");
        assertEquals(Optional.of(9), priority);
    }

    @Test
    void queryOneReturnsEmptyWhenNothingMatches() {
        Optional<String> none = dao.queryOne(
                "SELECT nombre FROM tipo_emergencia WHERE nombre = ?", row -> row.getString(1), "NO_EXISTE");
        assertTrue(none.isEmpty());
    }

    @Test
    void queryOneRejectsMoreThanOneRow() {
        assertThrows(DataAccessException.class,
                () -> dao.queryOne("SELECT nombre FROM tipo_emergencia", row -> row.getString(1)));
    }

    @Test
    void insertReturningIdGivesTheSequenceValueAndRollbackLeavesNoTrace() {
        long[] generated = new long[1];
        assertThrows(Rollback.class, () -> jdbc.runInTransaction(() -> {
            generated[0] = dao.insertReturningId(
                    "INSERT INTO tipo_emergencia (nombre, prioridad, descripcion) VALUES (?, ?, ?)",
                    "id_tipo_emergencia", "PRUEBA_JUNIT", 5, null);
            int updated = dao.execute(
                    "UPDATE tipo_emergencia SET prioridad = ? WHERE id_tipo_emergencia = ?", 6, generated[0]);
            assertEquals(1, updated);
            throw new Rollback();
        }));
        assertTrue(generated[0] > 0);
        assertTrue(dao.queryOne("SELECT 1 FROM tipo_emergencia WHERE nombre = ?",
                row -> row.getInt(1), "PRUEBA_JUNIT").isEmpty());
    }

    @Test
    void bindConvertsJavaTypesToTheOracleConventions() {
        assertEquals(Optional.of("S"), dao.queryOne("SELECT ? FROM dual", row -> row.getString(1), true));
        assertEquals(Optional.of("N"), dao.queryOne("SELECT ? FROM dual", row -> row.getString(1), false));
        assertEquals(Optional.of("ALTA"), dao.queryOne("SELECT ? FROM dual", row -> row.getString(1), Severity.HIGH));
        assertEquals(Optional.of("S"), dao.queryOne(
                "SELECT CASE WHEN ? IS NULL THEN 'S' ELSE 'N' END FROM dual", row -> row.getString(1), (Object) null));

        LocalDateTime moment = LocalDateTime.of(2026, 10, 15, 21, 30, 0);
        Optional<LocalDateTime> back = dao.queryOne("SELECT CAST(? AS TIMESTAMP) AS momento FROM dual",
                row -> AbstractJdbcDAO.getLocalDateTime(row, "momento"), moment);
        assertEquals(Optional.of(moment), back);
    }

    @Test
    void nullableReadersKeepNullInsteadOfZero() {
        List<Long> values = dao.queryList("SELECT CAST(NULL AS NUMBER) AS valor FROM dual",
                row -> AbstractJdbcDAO.getNullableLong(row, "valor"));
        assertEquals(1, values.size());
        assertNull(values.get(0));
        assertFalse(dao.queryOne("SELECT 'N' AS bandera FROM dual",
                row -> AbstractJdbcDAO.getFlag(row, "bandera")).orElseThrow());
    }

    @Test
    void sqlErrorsBecomeDataAccessException() {
        assertThrows(DataAccessException.class,
                () -> dao.queryList("SELECT * FROM tabla_que_no_existe", row -> row.getString(1)));
    }
}