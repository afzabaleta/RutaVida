package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dto.DashboardSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcDashboardDAO against the local Oracle. Each test creates its own rows (all named
 * PRUEBA A14) in a transaction that is rolled back, so Oracle is left unchanged.
 * The counts that do not depend on a date are checked as "one more than before"; the ones
 * of a day use 15/01/2031, a day without real data.
 * Skipped without Oracle or without the views and functions of 04_plsql.sql.
 */
class JdbcDashboardDAOIntegrationTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2031, 1, 15, 0, 0);
    private static final LocalDateTime NEXT_DAY = DAY.plusDays(1);

    private static JdbcTransactionManager jdbc;
    private static JdbcDashboardDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        assumeTrue(number(jdbc, "SELECT COUNT(*) FROM user_objects WHERE status = 'VALID' AND object_name IN"
                        + " ('VW_EMERGENCIAS_ACTIVAS', 'VW_UNIDADES_DISPONIBLES', 'VW_HOSPITALES_OPERATIVOS',"
                        + " 'FN_TIEMPO_RESPUESTA')") == 4,
                "Run database/04_plsql.sql first: dashboard tests skipped");
        dao = new JdbcDashboardDAO(jdbc);
    }

    private static DashboardSummary summaryOfTheDay() {
        return dao.summary(DAY, DAY, NEXT_DAY);
    }

    // ------------------------------------------------------------------ test rows

    private static void createUserAndStation() {
        run(jdbc, "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)"
                + " SELECT id_rol, 'PRUEBAA14001', 'Prueba', 'A14', 'prueba.a14@ejemplo.com', 'prueba_a14',"
                + " 'NO_ES_UN_HASH' FROM rol WHERE nombre_rol = 'COORDINADOR'");
        run(jdbc, "INSERT INTO estacion_emergencia (nombre, direccion, latitud, longitud)"
                + " VALUES ('ESTACION PRUEBA A14', 'Direccion de prueba', 10.4631, -73.2532)");
    }

    /** A unit of the test station; returns its id. */
    private static long createUnit(String code, String status) {
        run(jdbc, "INSERT INTO vehiculo_emergencia (id_tipo_vehiculo, id_estacion, codigo_unidad, estado_operativo)"
                + " SELECT t.id_tipo_vehiculo, e.id_estacion, ?, ? FROM tipo_vehiculo t, estacion_emergencia e"
                + " WHERE t.nombre = 'AMBULANCIA' AND e.nombre = 'ESTACION PRUEBA A14'", code, status);
        return number(jdbc, "SELECT id_vehiculo FROM vehiculo_emergencia WHERE codigo_unidad = ?", code);
    }

    /**
     * An emergency reported by the test user; returns its id. {@code reportedAt} and
     * {@code closedAt} are Oracle timestamp literals, for example TIMESTAMP '2031-01-15 08:00:00'.
     */
    private static long createEmergency(String place, String reportedAt, String status, String closedAt) {
        run(jdbc, "INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta, direccion_referencia, latitud,"
                + " longitud, fecha_hora_reporte, severidad, estado, fecha_hora_cierre)"
                + " SELECT t.id_tipo_emergencia, u.id_usuario, ?, 10.467172, -73.265099, " + reportedAt
                + ", 'ALTA', ?, " + closedAt + " FROM tipo_emergencia t, usuario u"
                + " WHERE t.nombre = 'EMERGENCIA_MEDICA' AND u.username = 'prueba_a14'", place, status);
        return number(jdbc, "SELECT id_emergencia FROM emergencia WHERE direccion_referencia = ?", place);
    }

    /** An assignment that already arrived; the times are timestamp literals. */
    private static void createArrival(long emergency, long unit, String status,
                                      String assignedAt, String departedAt, String arrivedAt) {
        run(jdbc, "INSERT INTO asignacion_emergencia (id_emergencia, id_vehiculo, id_usuario_coordinador,"
                + " fecha_asignacion, hora_salida, hora_llegada, estado)"
                + " SELECT ?, ?, id_usuario, " + assignedAt + ", " + departedAt + ", " + arrivedAt + ", ?"
                + " FROM usuario WHERE username = 'prueba_a14'", emergency, unit, status);
    }

    /** A road segment between two new nodes; returns its id. */
    private static long createSegment(String name) {
        for (String end : new String[]{"A", "B"}) {
            run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES (?, 10.46, -73.26)",
                    name + " " + end);
        }
        run(jdbc, "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m,"
                        + " velocidad_max_kmh) SELECT a.id_nodo, b.id_nodo, ?, 200, 40 FROM nodo_vial a, nodo_vial b"
                        + " WHERE a.referencia = ? AND b.referencia = ?",
                name, name + " A", name + " B");
        return number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = ?", name);
    }

    private static void createReading(long segment, String at, String level, double factor) {
        run(jdbc, "INSERT INTO trafico (id_tramo, fecha_hora, nivel, factor_congestion) VALUES (?, " + at + ", ?, ?)",
                segment, level, factor);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void theCountsOfTheMomentFollowTheData() {
        inRolledBackTransaction(jdbc, () -> {
            DashboardSummary before = summaryOfTheDay();

            createUserAndStation();
            createUnit("PRUEBA-A14-01", "DISPONIBLE");
            createUnit("PRUEBA-A14-02", "OCUPADO");
            createUnit("PRUEBA-A14-03", "MANTENIMIENTO");
            createEmergency("PRUEBA A14 activa", "SYSTIMESTAMP", "REPORTADA", "NULL");
            createEmergency("PRUEBA A14 cerrada", "TIMESTAMP '2030-01-01 08:00:00'", "CERRADA",
                    "TIMESTAMP '2030-01-01 09:00:00'");
            run(jdbc, "INSERT INTO hospital (nombre, nivel, direccion, latitud, longitud)"
                    + " VALUES ('PRUEBA A14 HOSPITAL', 'I', 'Direccion de prueba', 10.47, -73.25)");
            run(jdbc, "INSERT INTO hospital (nombre, nivel, direccion, latitud, longitud, estado)"
                    + " VALUES ('PRUEBA A14 HOSPITAL INACTIVO', 'I', 'Direccion de prueba', 10.47, -73.25, 'I')");
            long segment = createSegment("VIA PRUEBA A14");
            run(jdbc, "INSERT INTO incidente_vial (id_tramo, tipo_incidente, severidad, estado)"
                    + " VALUES (?, 'ACCIDENTE', 'ALTA', 'ACTIVO')", segment);
            run(jdbc, "INSERT INTO incidente_vial (id_tramo, tipo_incidente, severidad)"
                    + " VALUES (?, 'OBRA', 'BAJA')", segment);

            DashboardSummary after = summaryOfTheDay();

            assertEquals(before.activeEmergencies() + 1, after.activeEmergencies(), "the closed one does not count");
            assertEquals(before.availableUnits() + 1, after.availableUnits());
            assertEquals(before.busyUnits() + 1, after.busyUnits(), "the one in maintenance is neither");
            assertEquals(before.operationalHospitals() + 1, after.operationalHospitals(), "not the inactive one");
            assertEquals(before.activeIncidents() + 1, after.activeIncidents(), "not the PENDIENTE one");
        });
    }

    @Test
    void closedAndResponseTimeOfADay() {
        inRolledBackTransaction(jdbc, () -> {
            DashboardSummary empty = summaryOfTheDay();
            assertEquals(0, empty.closedToday());
            assertNull(empty.averageResponseMinutes(), "no arrival that day: null, not 0");

            createUserAndStation();
            long first = createUnit("PRUEBA-A14-01", "DISPONIBLE");
            long second = createUnit("PRUEBA-A14-02", "OCUPADO");
            long third = createUnit("PRUEBA-A14-03", "OCUPADO");

            // Reported 08:00, arrived 08:09 (9 min), closed 08:30.
            long closed = createEmergency("PRUEBA A14 cerrada", "TIMESTAMP '2031-01-15 08:00:00'", "CERRADA",
                    "TIMESTAMP '2031-01-15 08:30:00'");
            createArrival(closed, first, "FINALIZADA", "TIMESTAMP '2031-01-15 08:01:00'",
                    "TIMESTAMP '2031-01-15 08:02:00'", "TIMESTAMP '2031-01-15 08:09:00'");

            // Reported 10:00, two units: the first one arrived 10:12 (12 min), the other 10:20.
            long onScene = createEmergency("PRUEBA A14 en sitio", "TIMESTAMP '2031-01-15 10:00:00'", "EN_SITIO", "NULL");
            createArrival(onScene, second, "EN_SITIO", "TIMESTAMP '2031-01-15 10:01:00'",
                    "TIMESTAMP '2031-01-15 10:02:00'", "TIMESTAMP '2031-01-15 10:12:00'");
            createArrival(onScene, third, "EN_SITIO", "TIMESTAMP '2031-01-15 10:05:00'",
                    "TIMESTAMP '2031-01-15 10:06:00'", "TIMESTAMP '2031-01-15 10:20:00'");

            DashboardSummary day = summaryOfTheDay();

            assertEquals(1, day.closedToday());
            assertEquals(10.5, day.averageResponseMinutes(), 0.001, "(9 + 12) / 2: only the first unit counts");
            assertEquals(0, dao.summary(DAY, NEXT_DAY, NEXT_DAY.plusDays(1)).closedToday(), "another day");
        });
    }

    @Test
    void onlyTheLastRecentReadingOfEachSegmentCounts() {
        inRolledBackTransaction(jdbc, () -> {
            long stillCongested = createSegment("VIA PRUEBA A14 1");
            long nowFluid = createSegment("VIA PRUEBA A14 2");
            createReading(stillCongested, "TIMESTAMP '2031-01-15 07:00:00'", "FLUIDO", 1.0);
            createReading(stillCongested, "TIMESTAMP '2031-01-15 08:00:00'", "CONGESTIONADO", 2.5);
            createReading(nowFluid, "TIMESTAMP '2031-01-15 08:00:00'", "CONGESTIONADO", 2.5);
            createReading(nowFluid, "TIMESTAMP '2031-01-15 08:05:00'", "FLUIDO", 1.0);

            assertEquals(1, dao.summary(DAY.plusHours(7).plusMinutes(30), DAY, NEXT_DAY).congestedSegments());
            assertEquals(0, dao.summary(DAY.plusHours(8).plusMinutes(1), DAY, NEXT_DAY).congestedSegments(),
                    "the reading of 08:00 is too old");
        });
    }
}