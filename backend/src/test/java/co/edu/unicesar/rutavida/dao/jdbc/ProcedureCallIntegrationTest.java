package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests of callProcedure and callProcedureReturningLong against the PL/SQL
 * procedures of database/04_plsql.sql. They are SKIPPED, not failed, when there is no
 * db.properties, Oracle is not running or 04_plsql.sql was not run yet.
 * Every test runs inside a transaction that is rolled back: the database is left unchanged.
 */
class ProcedureCallIntegrationTest {

    private static final String ASSIGN = "{call sp_asignar_unidad(?, ?, ?, ?, ?)}";
    private static final String CLOSE = "{call sp_cerrar_emergencia(?, ?)}";

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

    /** Ids of the test rows created by {@link #createFixture}. */
    private record Fixture(long user, long vehicle, long emergency) {
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
        int procedures = dao.queryOne("SELECT COUNT(*) FROM user_objects WHERE object_type = 'PROCEDURE'"
                        + " AND status = 'VALID' AND object_name IN ('SP_ASIGNAR_UNIDAD', 'SP_CERRAR_EMERGENCIA')",
                row -> row.getInt(1)).orElse(0);
        assumeTrue(procedures == 2, "Run database/04_plsql.sql first: procedure tests skipped");
    }

    /** A coordinator, an available ambulance and an emergency reported ten minutes ago. */
    private static Fixture createFixture(LocalDateTime now) {
        long role = dao.queryOne("SELECT id_rol FROM rol WHERE nombre_rol = ?",
                row -> row.getLong(1), "COORDINADOR").orElseThrow();
        long user = dao.insertReturningId("INSERT INTO usuario (id_rol, documento, nombres, apellidos, email,"
                        + " username, password_hash) VALUES (?, ?, ?, ?, ?, ?, ?)", "id_usuario",
                role, "88888888", "PRUEBA", "JUNIT", "prueba.junit@ejemplo.com", "prueba_junit", "NO_ES_UN_HASH");
        long station = dao.insertReturningId("INSERT INTO estacion_emergencia (nombre, direccion, latitud, longitud)"
                        + " VALUES (?, ?, ?, ?)", "id_estacion",
                "ESTACION JUNIT", "Direccion de prueba", 10.4631, -73.2532);
        long vehicleType = dao.queryOne("SELECT id_tipo_vehiculo FROM tipo_vehiculo WHERE nombre = ?",
                row -> row.getLong(1), "AMBULANCIA").orElseThrow();
        long vehicle = dao.insertReturningId("INSERT INTO vehiculo_emergencia (id_tipo_vehiculo, id_estacion,"
                        + " codigo_unidad) VALUES (?, ?, ?)", "id_vehiculo",
                vehicleType, station, "JUNIT-01");
        long emergencyType = dao.queryOne("SELECT id_tipo_emergencia FROM tipo_emergencia WHERE nombre = ?",
                row -> row.getLong(1), "INCENDIO").orElseThrow();
        long emergency = dao.insertReturningId("INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta,"
                        + " direccion_referencia, latitud, longitud, fecha_hora_reporte, severidad)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)", "id_emergencia",
                emergencyType, user, "Direccion de prueba", 10.4640, -73.2540, now.minusMinutes(10), Severity.HIGH);
        return new Fixture(user, vehicle, emergency);
    }

    private static String text(String sql, long id) {
        return dao.queryOne(sql, row -> row.getString(1), id).orElseThrow();
    }

    @Test
    void assignAndCloseThroughTheProcedures() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        assertThrows(Rollback.class, () -> jdbc.runInTransaction(() -> {
            Fixture f = createFixture(now);

            long assignment = dao.callProcedureReturningLong(ASSIGN, f.emergency(), f.vehicle(), f.user(),
                    now.minusMinutes(9));
            assertTrue(assignment > 0);
            assertEquals("ASIGNADA", text("SELECT estado FROM emergencia WHERE id_emergencia = ?", f.emergency()));
            assertEquals("OCUPADO",
                    text("SELECT estado_operativo FROM vehiculo_emergencia WHERE id_vehiculo = ?", f.vehicle()));

            // The unit travels and arrives: this is what DispatchService will do later
            dao.execute("UPDATE asignacion_emergencia SET estado = 'EN_RUTA', hora_salida = ? WHERE id_asignacion = ?",
                    now.minusMinutes(8), assignment);
            dao.execute("UPDATE emergencia SET estado = 'EN_RUTA' WHERE id_emergencia = ?", f.emergency());
            dao.execute("UPDATE asignacion_emergencia SET estado = 'EN_SITIO', hora_llegada = ? WHERE id_asignacion = ?",
                    now.minusMinutes(2), assignment);
            dao.execute("UPDATE emergencia SET estado = 'EN_SITIO' WHERE id_emergencia = ?", f.emergency());

            // A PL/SQL function is read with a plain SELECT
            double minutes = dao.queryOne("SELECT fn_tiempo_respuesta(?) FROM dual",
                    row -> row.getDouble(1), assignment).orElseThrow();
            assertEquals(8.0, minutes, 0.001);

            dao.callProcedure(CLOSE, f.emergency(), now);
            assertEquals("CERRADA", text("SELECT estado FROM emergencia WHERE id_emergencia = ?", f.emergency()));
            assertEquals("FINALIZADA",
                    text("SELECT estado FROM asignacion_emergencia WHERE id_asignacion = ?", assignment));
            assertEquals("DISPONIBLE",
                    text("SELECT estado_operativo FROM vehiculo_emergencia WHERE id_vehiculo = ?", f.vehicle()));
            throw new Rollback();
        }));
        assertTrue(dao.queryOne("SELECT 1 FROM vehiculo_emergencia WHERE codigo_unidad = ?",
                row -> row.getInt(1), "JUNIT-01").isEmpty());
    }

    @Test
    void busyUnitIsABusinessRuleError() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        assertThrows(Rollback.class, () -> jdbc.runInTransaction(() -> {
            Fixture f = createFixture(now);
            dao.callProcedureReturningLong(ASSIGN, f.emergency(), f.vehicle(), f.user(), null);

            BusinessRuleException busy = assertThrows(BusinessRuleException.class,
                    () -> dao.callProcedureReturningLong(ASSIGN, f.emergency(), f.vehicle(), f.user(), null));
            assertEquals("La unidad " + f.vehicle() + " no esta disponible (OCUPADO)", busy.getMessage());

            BusinessRuleException early = assertThrows(BusinessRuleException.class,
                    () -> dao.callProcedure(CLOSE, f.emergency(), now));
            assertEquals("No se puede cerrar una emergencia en estado ASIGNADA", early.getMessage());
            throw new Rollback();
        }));
    }

    @Test
    void unknownEmergencyIsABusinessRuleError() {
        BusinessRuleException thrown = assertThrows(BusinessRuleException.class,
                () -> dao.callProcedure(CLOSE, -1L, null));
        assertEquals("La emergencia -1 no existe", thrown.getMessage());
    }

    @Test
    void otherSqlErrorsStayDataAccessException() {
        assertThrows(DataAccessException.class,
                () -> dao.callProcedure("{call sp_que_no_existe(?)}", 1));
    }
}