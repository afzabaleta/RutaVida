package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dto.OperationsReport;
import co.edu.unicesar.rutavida.dto.ReportCount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcReportDAO against Oracle. Each test creates its own rows (all named PRUEBA A23) in a
 * transaction that is rolled back, so Oracle is left unchanged. The rows are on 10 and
 * 11/02/2031, days without real data, so the counts are exact.
 * Skipped without Oracle, without fn_tiempo_respuesta (04_plsql.sql) or without
 * REPORTE_PANICO (07_boton_panico.sql).
 */
class JdbcReportDAOIntegrationTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2031, 2, 10);
    private static final LocalDate SECOND_DAY = LocalDate.of(2031, 2, 11);

    private static JdbcTransactionManager jdbc;
    private static JdbcReportDAO dao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        assumeTrue(number(jdbc, "SELECT COUNT(*) FROM user_objects"
                        + " WHERE status = 'VALID' AND object_name IN ('FN_TIEMPO_RESPUESTA', 'REPORTE_PANICO')") == 2,
                "Run database/04_plsql.sql and 07_boton_panico.sql first: report tests skipped");
        dao = new JdbcReportDAO(jdbc);
    }

    // ------------------------------------------------------------------ test rows

    private static void createUserAndStation() {
        run(jdbc, "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username, password_hash)"
                + " SELECT id_rol, 'PRUEBAA23001', 'Prueba', 'A23', 'prueba.a23@ejemplo.com', 'prueba_a23',"
                + " 'NO_ES_UN_HASH' FROM rol WHERE nombre_rol = 'COORDINADOR'");
        run(jdbc, "INSERT INTO estacion_emergencia (nombre, direccion, latitud, longitud)"
                + " VALUES ('ESTACION PRUEBA A23', 'Direccion de prueba', 10.4631, -73.2532)");
    }

    private static long createUnit(String code, String type) {
        run(jdbc, "INSERT INTO vehiculo_emergencia (id_tipo_vehiculo, id_estacion, codigo_unidad, estado_operativo)"
                + " SELECT t.id_tipo_vehiculo, e.id_estacion, ?, 'OCUPADO' FROM tipo_vehiculo t, estacion_emergencia e"
                + " WHERE t.nombre = ? AND e.nombre = 'ESTACION PRUEBA A23'", code, type);
        return number(jdbc, "SELECT id_vehiculo FROM vehiculo_emergencia WHERE codigo_unidad = ?", code);
    }

    /** {@code reportedAt} and {@code closedAt} are Oracle timestamp literals or NULL. */
    private static long createEmergency(String place, String type, String severity, String reportedAt,
                                        String status, String closedAt) {
        run(jdbc, "INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta, direccion_referencia, latitud,"
                + " longitud, fecha_hora_reporte, severidad, estado, fecha_hora_cierre)"
                + " SELECT t.id_tipo_emergencia, u.id_usuario, ?, 10.467172, -73.265099, " + reportedAt
                + ", ?, ?, " + closedAt + " FROM tipo_emergencia t, usuario u"
                + " WHERE t.nombre = ? AND u.username = 'prueba_a23'", place, severity, status, type);
        return number(jdbc, "SELECT id_emergencia FROM emergencia WHERE direccion_referencia = ?", place);
    }

    /** An assignment; {@code arrivedAt} is NULL when the unit did not arrive. */
    private static void createAssignment(long emergency, long unit, String status, String assignedAt,
                                         String departedAt, String arrivedAt) {
        run(jdbc, "INSERT INTO asignacion_emergencia (id_emergencia, id_vehiculo, id_usuario_coordinador,"
                + " fecha_asignacion, hora_salida, hora_llegada, estado)"
                + " SELECT ?, ?, id_usuario, " + assignedAt + ", " + departedAt + ", " + arrivedAt + ", ?"
                + " FROM usuario WHERE username = 'prueba_a23'", emergency, unit, status);
    }

    private static void createRoute(long emergency, long unit, String type, String recommended, String status,
                                    String generatedAt) {
        run(jdbc, "INSERT INTO ruta (id_emergencia, id_vehiculo, tipo_ruta, distancia_km, tiempo_estimado_min,"
                + " costo_total, fecha_generacion, es_recomendada, estado)"
                + " VALUES (?, ?, ?, 2.5, 6, 360, " + generatedAt + ", ?, ?)", emergency, unit, type, recommended, status);
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

    // ------------------------------------------------------------------ tests

    @Test
    void anEmptyPeriodHasZerosAndNullAverages() {
        OperationsReport report = dao.operations(FIRST_DAY, SECOND_DAY);

        assertEquals(0, report.reportedEmergencies());
        assertEquals(0, report.attendedEmergencies());
        assertNull(report.averageResponseMinutes(), "no arrival: null, not 0");
        assertNull(report.fastestResponseMinutes());
        assertEquals(List.of(), report.byDay(), "the service fills the empty days");
    }

    @Test
    void theReportCountsTheEmergenciesOfThePeriod() {
        inRolledBackTransaction(jdbc, () -> {
            createUserAndStation();
            long ambulance = createUnit("PRUEBA-A23-AMB", "AMBULANCIA");
            long secondAmbulance = createUnit("PRUEBA-A23-AMB2", "AMBULANCIA");
            long engine = createUnit("PRUEBA-A23-BOM", "BOMBERO");

            // Day 1, 08:00: medical, arrived 08:09 (9 min), closed.
            long medical = createEmergency("PRUEBA A23 medica", "EMERGENCIA_MEDICA", "ALTA",
                    "TIMESTAMP '2031-02-10 08:00:00'", "CERRADA", "TIMESTAMP '2031-02-10 08:40:00'");
            createAssignment(medical, ambulance, "FINALIZADA", "TIMESTAMP '2031-02-10 08:01:00'",
                    "TIMESTAMP '2031-02-10 08:02:00'", "TIMESTAMP '2031-02-10 08:09:00'");
            createRoute(medical, ambulance, "PRINCIPAL", "S", "DESCARTADA", "TIMESTAMP '2031-02-10 08:01:00'");
            createRoute(medical, ambulance, "ALTERNATIVA", "N", "DESCARTADA", "TIMESTAMP '2031-02-10 08:01:00'");
            createRoute(medical, ambulance, "PRINCIPAL", "S", "FINALIZADA", "TIMESTAMP '2031-02-10 08:05:00'");

            // Day 2, 10:00: fire with two units; the first one arrived 10:15 (15 min), the other 10:21.
            long fire = createEmergency("PRUEBA A23 incendio", "INCENDIO", "CRITICA",
                    "TIMESTAMP '2031-02-11 10:00:00'", "EN_SITIO", "NULL");
            createAssignment(fire, engine, "EN_SITIO", "TIMESTAMP '2031-02-11 10:01:00'",
                    "TIMESTAMP '2031-02-11 10:02:00'", "TIMESTAMP '2031-02-11 10:15:00'");
            createAssignment(fire, secondAmbulance, "EN_SITIO", "TIMESTAMP '2031-02-11 10:03:00'",
                    "TIMESTAMP '2031-02-11 10:04:00'", "TIMESTAMP '2031-02-11 10:21:00'");
            createRoute(fire, engine, "PRINCIPAL", "S", "FINALIZADA", "TIMESTAMP '2031-02-11 10:01:00'");

            // Day 2, 11:00: medical without a unit yet.
            createEmergency("PRUEBA A23 sin unidad", "EMERGENCIA_MEDICA", "MEDIA",
                    "TIMESTAMP '2031-02-11 11:00:00'", "REPORTADA", "NULL");

            // The day after the period: does not count.
            createEmergency("PRUEBA A23 fuera", "RESCATE", "BAJA",
                    "TIMESTAMP '2031-02-12 09:00:00'", "REPORTADA", "NULL");

            long segment = createSegment("VIA PRUEBA A23");
            run(jdbc, "INSERT INTO incidente_vial (id_tramo, tipo_incidente, severidad, fecha_inicio)"
                    + " VALUES (?, 'OBRA', 'BAJA', TIMESTAMP '2031-02-10 07:00:00')", segment);
            run(jdbc, "INSERT INTO reporte_panico (latitud, longitud, fecha_hora, id_dispositivo)"
                    + " VALUES (10.4672, -73.2651, TIMESTAMP '2031-02-11 09:00:00', 'PRUEBA-A23-DISPOSITIVO')");

            OperationsReport report = dao.operations(FIRST_DAY, SECOND_DAY);

            assertEquals(3, report.reportedEmergencies(), "not the one of the next day");
            assertEquals(1, report.closedEmergencies());
            assertEquals(2, report.attendedEmergencies());
            assertEquals(12.0, report.averageResponseMinutes(), 0.001, "(9 + 15) / 2: the first unit of each");
            assertEquals(9.0, report.fastestResponseMinutes(), 0.001);
            assertEquals(15.0, report.slowestResponseMinutes(), 0.001);
            assertEquals(3, report.dispatches());
            assertEquals(4, report.routesGenerated());
            assertEquals(1, report.recalculations(), "the second recommended route of the medical mission");
            assertEquals(1, report.roadIncidents());
            assertEquals(1, report.panicReports());
            assertEquals(0, report.validatedPanicReports());

            assertEquals(List.of(new ReportCount("EMERGENCIA_MEDICA", 2, 9.0), new ReportCount("INCENDIO", 1, 15.0)),
                    report.byEmergencyType(), "the most frequent first; the unattended one has no time");
            assertEquals(List.of("CRITICA", "ALTA", "MEDIA"),
                    report.bySeverity().stream().map(ReportCount::label).toList());
            assertEquals(new ReportCount("AMBULANCIA", 2, 15.0), report.byVehicleType().get(0),
                    "(9 + 21) / 2: every ambulance that arrived");
            assertEquals(new ReportCount("BOMBERO", 1, 15.0), report.byVehicleType().get(1));
            assertEquals(List.of(new ReportCount("2031-02-10", 1, 9.0), new ReportCount("2031-02-11", 2, 15.0)),
                    report.byDay());
        });
    }
}
