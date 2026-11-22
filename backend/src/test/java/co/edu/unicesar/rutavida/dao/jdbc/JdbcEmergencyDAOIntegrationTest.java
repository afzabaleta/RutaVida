package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcEmergencyTypeDAO and JdbcEmergencyDAO against the local Oracle. The types come from
 * 02_datos_iniciales.sql; each emergency test creates its own user (PRUEBA_A7…) in a
 * transaction that is rolled back, so Oracle is left unchanged. Skipped without Oracle.
 */
class JdbcEmergencyDAOIntegrationTest {

    private static final LocalDateTime REPORTED = LocalDateTime.of(2026, 11, 3, 21, 15, 30);
    /** Parque La Popa, Valledupar (6 decimals, like the NUMBER(9,6) columns). */
    private static final GeoPoint POPA = new GeoPoint(10.467172, -73.265099);

    private static JdbcTransactionManager jdbc;
    private static JdbcEmergencyTypeDAO typeDao;
    private static JdbcEmergencyDAO emergencyDao;
    private static long accidentType;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        typeDao = new JdbcEmergencyTypeDAO(jdbc);
        emergencyDao = new JdbcEmergencyDAO(jdbc);
        accidentType = number(jdbc, "SELECT id_tipo_emergencia FROM tipo_emergencia WHERE nombre = 'ACCIDENTE_VIAL'");
    }

    /** A test user that reports the emergencies; returns its id. Only inside a rolled-back transaction. */
    private static long insertTestUser() {
        run(jdbc, "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)"
                + " SELECT id_rol, 'PRUEBAA7001', 'Prueba', 'A7', 'prueba.a7@ejemplo.com', 'prueba_a7', 'NO_ES_UN_HASH'"
                + " FROM rol WHERE nombre_rol = 'OPERADOR_CENTRAL'");
        return number(jdbc, "SELECT id_usuario FROM usuario WHERE username = 'prueba_a7'");
    }

    private static Emergency newEmergency(long userId, String address, LocalDateTime reportedAt) {
        return new Emergency(0, accidentType, userId, address, POPA, reportedAt, Severity.HIGH,
                null, EmergencyStatus.REPORTED, null);
    }

    @Test
    void theCatalogOfTypesIsReadByPriority() {
        List<EmergencyType> types = typeDao.findAll();

        List<String> names = types.stream().map(EmergencyType::getName).toList();
        assertTrue(names.containsAll(List.of("EMERGENCIA_MEDICA", "INCENDIO", "ACCIDENTE_VIAL", "RESCATE")));
        for (int i = 1; i < types.size(); i++) {
            assertTrue(types.get(i - 1).getPriority() >= types.get(i).getPriority(), "highest priority first");
        }
        EmergencyType accident = typeDao.findById(accidentType).orElseThrow();
        assertEquals("ACCIDENTE_VIAL", accident.getName());
        assertEquals(8, accident.getPriority());
        assertTrue(typeDao.findById(-1).isEmpty());
    }

    @Test
    void anEmergencyIsInsertedAndReadBack() {
        inRolledBackTransaction(jdbc, () -> {
            long user = insertTestUser();
            Emergency emergency = new Emergency(0, accidentType, user, "Frente al Parque La Popa", POPA,
                    REPORTED, Severity.CRITICAL, "Choque de prueba con dos vehículos", EmergencyStatus.REPORTED, null);

            long id = emergencyDao.insert(emergency);

            assertTrue(id > 0);
            assertEquals(id, emergency.getId(), "insert assigns the id to the object");
            Emergency read = emergencyDao.findById(id).orElseThrow();
            assertEquals(accidentType, read.getEmergencyTypeId());
            assertEquals(user, read.getReportedByUserId());
            assertEquals("Frente al Parque La Popa", read.getAddressReference());
            assertEquals(POPA, read.getLocation());
            assertEquals(REPORTED, read.getReportedAt(), "the report time is the one given, not SYSTIMESTAMP");
            assertEquals(Severity.CRITICAL, read.getSeverity());
            assertEquals("Choque de prueba con dos vehículos", read.getDescription());
            assertEquals(EmergencyStatus.REPORTED, read.getStatus());
            assertNull(read.getClosedAt());
        });
        assertTrue(emergencyDao.findById(-1).isEmpty());
    }

    @Test
    void activeAndPeriodQueriesAndTheStatusChange() {
        inRolledBackTransaction(jdbc, () -> {
            long user = insertTestUser();
            Emergency open = newEmergency(user, "Calle de prueba 1", REPORTED);
            Emergency closed = newEmergency(user, "Calle de prueba 2", REPORTED.plusMinutes(5));
            emergencyDao.insert(open);
            emergencyDao.insert(closed);

            emergencyDao.updateStatus(open.getId(), EmergencyStatus.ASSIGNED, null);
            emergencyDao.updateStatus(closed.getId(), EmergencyStatus.CLOSED, REPORTED.plusMinutes(40));

            Emergency assigned = emergencyDao.findById(open.getId()).orElseThrow();
            assertEquals(EmergencyStatus.ASSIGNED, assigned.getStatus());
            Emergency done = emergencyDao.findById(closed.getId()).orElseThrow();
            assertEquals(EmergencyStatus.CLOSED, done.getStatus());
            assertEquals(REPORTED.plusMinutes(40), done.getClosedAt());

            List<Long> active = emergencyDao.findActive().stream().map(Emergency::getId).toList();
            assertTrue(active.contains(open.getId()));
            assertFalse(active.contains(closed.getId()), "a closed emergency is not active");

            List<Long> period = emergencyDao.findByPeriod(REPORTED, REPORTED.plusMinutes(5)).stream()
                    .map(Emergency::getId).filter(id -> id == open.getId() || id == closed.getId()).toList();
            assertEquals(List.of(open.getId(), closed.getId()), period, "both, oldest first");

            assertEquals(1, number(jdbc, "SELECT COUNT(*) FROM auditoria WHERE modulo = 'EMERGENCIAS'"
                            + " AND operacion = 'CAMBIO_ESTADO' AND registro_id = ? AND detalle = 'REPORTADA -> ASIGNADA'",
                    open.getId()), "the trigger audits the status change");
        });
    }

    @Test
    void anEmergencyIsClosedByTheProcedure() {
        assumeTrue(number(jdbc, "SELECT COUNT(*) FROM user_objects WHERE object_name = 'SP_CERRAR_EMERGENCIA'"
                + " AND status = 'VALID'") == 1, "Run database/04_plsql.sql first: test skipped");
        inRolledBackTransaction(jdbc, () -> {
            long user = insertTestUser();
            Emergency falseAlarm = newEmergency(user, "Calle de prueba 3", REPORTED);
            Emergency onItsWay = newEmergency(user, "Calle de prueba 4", REPORTED);
            emergencyDao.insert(falseAlarm);
            emergencyDao.insert(onItsWay);
            emergencyDao.updateStatus(onItsWay.getId(), EmergencyStatus.ASSIGNED, null);

            emergencyDao.close(falseAlarm.getId(), REPORTED.plusMinutes(30));

            Emergency closed = emergencyDao.findById(falseAlarm.getId()).orElseThrow();
            assertEquals(EmergencyStatus.CLOSED, closed.getStatus());
            assertEquals(REPORTED.plusMinutes(30), closed.getClosedAt());
            assertEquals("La emergencia " + falseAlarm.getId() + " ya esta cerrada", assertThrows(
                    BusinessRuleException.class, () -> emergencyDao.close(falseAlarm.getId(), REPORTED.plusMinutes(31)))
                    .getMessage());
            assertEquals("No se puede cerrar una emergencia en estado ASIGNADA", assertThrows(
                    BusinessRuleException.class, () -> emergencyDao.close(onItsWay.getId(), REPORTED.plusMinutes(30)))
                    .getMessage());
        });
        assertEquals("La emergencia -1 no existe", assertThrows(BusinessRuleException.class,
                () -> emergencyDao.close(-1, REPORTED)).getMessage());
    }

    @Test
    void wrongCallsAreRejected() {
        Emergency saved = newEmergency(1, "Calle de prueba", REPORTED);
        saved.setId(5);
        assertThrows(IllegalArgumentException.class, () -> emergencyDao.insert(saved));
        assertThrows(NotFoundException.class,
                () -> emergencyDao.updateStatus(-1, EmergencyStatus.ASSIGNED, null));
    }
}