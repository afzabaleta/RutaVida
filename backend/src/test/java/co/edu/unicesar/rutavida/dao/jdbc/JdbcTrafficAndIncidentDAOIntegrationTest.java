package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.TrafficReading;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcTrafficReadingDAO and JdbcRoadIncidentDAO against the local Oracle. Each test
 * creates its own two nodes and one segment (reference PRUEBA_A5_…) in a transaction
 * that is rolled back. Skipped without Oracle.
 */
class JdbcTrafficAndIncidentDAOIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 7, 30);

    private static JdbcTransactionManager jdbc;
    private static JdbcTrafficReadingDAO trafficDao;
    private static JdbcRoadIncidentDAO incidentDao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        trafficDao = new JdbcTrafficReadingDAO(jdbc);
        incidentDao = new JdbcRoadIncidentDAO(jdbc);
    }

    /** Two test nodes and one open segment between them; returns the id of the segment. */
    private static long insertTestSegment() {
        run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES ('PRUEBA_A5_A', 10.4600, -73.2500)");
        run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud) VALUES ('PRUEBA_A5_B', 10.4610, -73.2510)");
        run(jdbc, "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, nombre_via, distancia_m, velocidad_max_kmh)"
                + " SELECT a.id_nodo, b.id_nodo, 'VIA PRUEBA A5', 150, 30 FROM nodo_vial a, nodo_vial b"
                + " WHERE a.referencia = 'PRUEBA_A5_A' AND b.referencia = 'PRUEBA_A5_B'");
        return number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = 'VIA PRUEBA A5'");
    }

    private static RoadIncident newIncident(long segmentId, String description) {
        return new RoadIncident(0, segmentId, IncidentType.ACCIDENT, Severity.HIGH, false,
                description, START, null, IncidentStatus.PENDING, IncidentOrigin.OPERATOR);
    }

    private static long auditRows(long incidentId, String change) {
        return number(jdbc, "SELECT COUNT(*) FROM auditoria WHERE modulo = 'INCIDENTES'"
                + " AND operacion = 'CAMBIO_ESTADO' AND registro_id = ? AND detalle = ?", incidentId, change);
    }

    @Test
    void insertBatchSavesEveryColumnOfEveryReading() {
        inRolledBackTransaction(jdbc, () -> {
            long segment = insertTestSegment();
            trafficDao.insertBatch(List.of(
                    new TrafficReading(0, segment, START, TrafficLevel.CONGESTED, 12.5, 2.4, TrafficSource.SIMULATION),
                    new TrafficReading(0, segment, START.plusMinutes(1), TrafficLevel.MODERATE, null, 1.5,
                            TrafficSource.SENSOR),
                    new TrafficReading(0, segment, START.plusMinutes(2), TrafficLevel.FLUID, 45.0, 1.0,
                            TrafficSource.SIMULATION)));

            assertEquals(3, number(jdbc, "SELECT COUNT(*) FROM trafico WHERE id_tramo = ?", segment));
            assertEquals(1, number(jdbc, "SELECT COUNT(*) FROM trafico WHERE id_tramo = ?"
                    + " AND TO_CHAR(fecha_hora, 'YYYY-MM-DD HH24:MI:SS') = '2026-10-24 07:30:00'"
                    + " AND nivel = 'CONGESTIONADO' AND velocidad_promedio_kmh = 12.5"
                    + " AND factor_congestion = 2.4 AND fuente = 'SIMULACION'", segment));
            assertEquals(1, number(jdbc, "SELECT COUNT(*) FROM trafico WHERE id_tramo = ?"
                    + " AND nivel = 'MODERADO' AND velocidad_promedio_kmh IS NULL AND fuente = 'SENSOR'", segment));
        });
    }

    @Test
    void aBatchBiggerThanOneRoundTripIsSavedWhole() {
        int total = JdbcTrafficReadingDAO.BATCH_SIZE * 2 + 7;
        inRolledBackTransaction(jdbc, () -> {
            long segment = insertTestSegment();
            List<TrafficReading> readings = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                readings.add(new TrafficReading(0, segment, START.plusSeconds(i), TrafficLevel.FLUID,
                        i % 2 == 0 ? null : 40.0, 1.0, TrafficSource.SIMULATION));
            }

            trafficDao.insertBatch(readings);

            assertEquals(total, number(jdbc, "SELECT COUNT(*) FROM trafico WHERE id_tramo = ?", segment));
        });
    }

    @Test
    void insertBatchChecksItsInputBeforeWriting() {
        trafficDao.insertBatch(List.of());

        TrafficReading saved = new TrafficReading(0, 1, START, TrafficLevel.FLUID, null, 1.0, TrafficSource.MANUAL);
        saved.assignId(5);
        assertThrows(IllegalArgumentException.class, () -> trafficDao.insertBatch(List.of(saved)));
        assertThrows(NullPointerException.class, () -> trafficDao.insertBatch(null));
    }

    @Test
    void anIncidentIsInsertedAndReadBack() {
        inRolledBackTransaction(jdbc, () -> {
            long segment = insertTestSegment();
            RoadIncident incident = newIncident(segment, "  Choque en la vía de prueba  ");

            long id = incidentDao.insert(incident);

            assertTrue(id > 0);
            assertEquals(id, incident.getId(), "insert assigns the id to the object");
            RoadIncident read = incidentDao.findById(id).orElseThrow();
            assertEquals(segment, read.getSegmentId());
            assertEquals(IncidentType.ACCIDENT, read.getType());
            assertEquals(Severity.HIGH, read.getSeverity());
            assertFalse(read.isBlocksSegment());
            assertEquals("Choque en la vía de prueba", read.getDescription());
            assertEquals(START, read.getStartedAt());
            assertNull(read.getEndedAt());
            assertEquals(IncidentStatus.PENDING, read.getStatus());
            assertEquals(IncidentOrigin.OPERATOR, read.getOrigin());
            assertFalse(read.affectsRouting(), "a pending incident does not change routes (RN-05)");
        });
    }

    @Test
    void theLifeCycleIsSavedAndTheTriggerAuditsEachChange() {
        inRolledBackTransaction(jdbc, () -> {
            long segment = insertTestSegment();
            RoadIncident incident = newIncident(segment, null);
            long id = incidentDao.insert(incident);

            incident.activate(true);
            incidentDao.update(incident);
            RoadIncident active = incidentDao.findById(id).orElseThrow();
            assertEquals(IncidentStatus.ACTIVE, active.getStatus());
            assertTrue(active.blocksTraffic());
            assertEquals(1, auditRows(id, "PENDIENTE -> ACTIVO"));

            incident.close(START.plusHours(2));
            incidentDao.update(incident);
            RoadIncident closed = incidentDao.findById(id).orElseThrow();
            assertEquals(IncidentStatus.CLOSED, closed.getStatus());
            assertEquals(START.plusHours(2), closed.getEndedAt());
            assertFalse(closed.affectsRouting());
            assertEquals(1, auditRows(id, "ACTIVO -> CERRADO"));
        });
    }

    @Test
    void findByStatusReturnsOnlyThatStatusOldestFirst() {
        inRolledBackTransaction(jdbc, () -> {
            long segment = insertTestSegment();
            RoadIncident pending = newIncident(segment, "PRUEBA_A5 pendiente");
            RoadIncident active = newIncident(segment, "PRUEBA_A5 activo");
            incidentDao.insert(pending);
            incidentDao.insert(active);
            active.activate(false);
            incidentDao.update(active);

            List<RoadIncident> actives = incidentDao.findByStatus(IncidentStatus.ACTIVE);
            assertTrue(actives.stream().anyMatch(i -> i.getId() == active.getId()));
            assertFalse(actives.stream().anyMatch(i -> i.getId() == pending.getId()));
            assertTrue(actives.stream().allMatch(i -> i.getStatus() == IncidentStatus.ACTIVE));

            List<RoadIncident> pendings = incidentDao.findByStatus(IncidentStatus.PENDING);
            assertTrue(pendings.stream().anyMatch(i -> i.getId() == pending.getId()));
            for (int i = 1; i < pendings.size(); i++) {
                assertFalse(pendings.get(i).getStartedAt().isBefore(pendings.get(i - 1).getStartedAt()),
                        "oldest first");
            }
        });
    }

    @Test
    void wrongCallsAreRejected() {
        assertTrue(incidentDao.findById(-1).isEmpty());

        RoadIncident unsaved = newIncident(1, null);
        assertThrows(IllegalArgumentException.class, () -> incidentDao.update(unsaved));

        RoadIncident missing = new RoadIncident(999_999_999, 1, IncidentType.OTHER, Severity.LOW, false,
                null, START, null, IncidentStatus.PENDING, IncidentOrigin.OPERATOR);
        assertThrows(NotFoundException.class, () -> incidentDao.update(missing));
        assertThrows(IllegalArgumentException.class, () -> incidentDao.insert(missing));
        assertThrows(NullPointerException.class, () -> incidentDao.findByStatus(null));
    }
}