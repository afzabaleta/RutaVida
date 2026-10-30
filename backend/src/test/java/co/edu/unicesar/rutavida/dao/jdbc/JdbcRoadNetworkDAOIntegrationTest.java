package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.RoadNode;
import co.edu.unicesar.rutavida.model.RoadSegment;
import co.edu.unicesar.rutavida.model.TrafficLight;
import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToLongFunction;

import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.inRolledBackTransaction;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.number;
import static co.edu.unicesar.rutavida.dao.jdbc.OracleTestDatabase.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcRoadNodeDAO, JdbcRoadSegmentDAO and JdbcTrafficLightDAO against the local Oracle.
 * The test rows (reference PRUEBA_A4_…) are written in transactions that are rolled back,
 * so the real network loaded from OpenStreetMap is not touched. Skipped without Oracle.
 */
class JdbcRoadNetworkDAOIntegrationTest {

    /** OpenStreetMap ids far above the real ones, so they never collide with the loaded network. */
    private static final long TEST_OSM_NODE = 999_999_999_001L;
    private static final long TEST_OSM_WAY = 999_999_999_002L;

    private static JdbcTransactionManager jdbc;
    private static JdbcRoadNodeDAO nodeDao;
    private static JdbcRoadSegmentDAO segmentDao;
    private static JdbcTrafficLightDAO lightDao;

    @BeforeAll
    static void connect() {
        jdbc = OracleTestDatabase.connectOrSkip();
        nodeDao = new JdbcRoadNodeDAO(jdbc);
        segmentDao = new JdbcRoadSegmentDAO(jdbc);
        lightDao = new JdbcTrafficLightDAO(jdbc);
    }

    /** Inserts the two test nodes and returns their ids: [A, B]. */
    private static long[] insertTwoNodes() {
        run(jdbc, "INSERT INTO nodo_vial (osm_id, referencia, latitud, longitud, tipo_nodo, estado)"
                + " VALUES (?, 'PRUEBA_A4_A', 10.467172, -73.265099, 'GLORIETA', 'A')", TEST_OSM_NODE);
        run(jdbc, "INSERT INTO nodo_vial (referencia, latitud, longitud, estado)"
                + " VALUES ('PRUEBA_A4_B', 10.468000, -73.264000, 'I')");
        return new long[]{
                number(jdbc, "SELECT id_nodo FROM nodo_vial WHERE referencia = 'PRUEBA_A4_A'"),
                number(jdbc, "SELECT id_nodo FROM nodo_vial WHERE referencia = 'PRUEBA_A4_B'")};
    }

    /** Inserts an open segment between two nodes and returns its id. */
    private static long insertSegment(long from, long to, String roadName) {
        run(jdbc, "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, osm_way_id, nombre_via,"
                        + " distancia_m, velocidad_max_kmh) VALUES (?, ?, ?, ?, 250.5, 30)",
                from, to, TEST_OSM_WAY, roadName);
        return number(jdbc, "SELECT id_tramo FROM tramo_vial WHERE nombre_via = ?", roadName);
    }

    private static <T> T withId(List<T> all, ToLongFunction<T> id, long wanted) {
        return all.stream()
                .filter(item -> id.applyAsLong(item) == wanted)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Row " + wanted + " not returned by findAll"));
    }

    private static <T> void assertOrderedById(List<T> all, ToLongFunction<T> id) {
        for (int i = 1; i < all.size(); i++) {
            assertTrue(id.applyAsLong(all.get(i - 1)) < id.applyAsLong(all.get(i)), "findAll must be ordered by id");
        }
    }

    @Test
    void nodesAreReadWithEveryColumn() {
        inRolledBackTransaction(jdbc, () -> {
            long[] ids = insertTwoNodes();
            List<RoadNode> nodes = nodeDao.findAll();

            RoadNode a = withId(nodes, RoadNode::getId, ids[0]);
            assertEquals(TEST_OSM_NODE, a.getOsmId());
            assertEquals("PRUEBA_A4_A", a.getReference());
            assertEquals(10.467172, a.getLocation().latitude(), 1e-6);
            assertEquals(-73.265099, a.getLocation().longitude(), 1e-6);
            assertEquals(NodeType.ROUNDABOUT, a.getNodeType());
            assertEquals(RecordStatus.ACTIVE, a.getStatus());

            RoadNode b = withId(nodes, RoadNode::getId, ids[1]);
            assertNull(b.getOsmId(), "a NULL osm_id must stay null, not 0");
            assertEquals(NodeType.INTERSECTION, b.getNodeType(), "default of tipo_nodo");
            assertEquals(RecordStatus.INACTIVE, b.getStatus());

            assertOrderedById(nodes, RoadNode::getId);
        });
    }

    @Test
    void segmentsAreReadAndTheBaseTimeMatchesOracle() {
        inRolledBackTransaction(jdbc, () -> {
            long[] ids = insertTwoNodes();
            long id = insertSegment(ids[0], ids[1], "VIA PRUEBA A4");
            List<RoadSegment> segments = segmentDao.findAll();

            RoadSegment segment = withId(segments, RoadSegment::getId, id);
            assertEquals(ids[0], segment.getFromNodeId());
            assertEquals(ids[1], segment.getToNodeId());
            assertEquals(TEST_OSM_WAY, segment.getOsmWayId());
            assertEquals("VIA PRUEBA A4", segment.getRoadName());
            assertEquals(250.5, segment.getLengthMeters(), 1e-9);
            assertEquals(30, segment.getMaxSpeedKmh(), 1e-9);
            assertEquals(RoadSegmentStatus.OPEN, segment.getStatus(), "default of estado");

            // Oracle rounds the virtual column to 2 decimals; Java keeps every decimal.
            long oracleCentiseconds = number(jdbc,
                    "SELECT tiempo_base_seg * 100 FROM tramo_vial WHERE id_tramo = ?", id);
            assertEquals(oracleCentiseconds / 100.0, segment.getLengthMeters() / (segment.getMaxSpeedKmh() / 3.6), 0.01);

            assertOrderedById(segments, RoadSegment::getId);
        });
    }

    @Test
    void updateStatusChangesOnlyThatSegment() {
        inRolledBackTransaction(jdbc, () -> {
            long[] ids = insertTwoNodes();
            long goes = insertSegment(ids[0], ids[1], "VIA PRUEBA A4 IDA");
            long comesBack = insertSegment(ids[1], ids[0], "VIA PRUEBA A4 VUELTA");

            segmentDao.updateStatus(goes, RoadSegmentStatus.CLOSED);

            List<RoadSegment> segments = segmentDao.findAll();
            assertEquals(RoadSegmentStatus.CLOSED, withId(segments, RoadSegment::getId, goes).getStatus());
            assertEquals(RoadSegmentStatus.CLOSED, withId(segments, RoadSegment::getId, goes).getStatus());
            assertEquals(RoadSegmentStatus.OPEN, withId(segments, RoadSegment::getId, comesBack).getStatus());

            segmentDao.updateStatus(goes, RoadSegmentStatus.RESTRICTED);
            assertEquals(1, number(jdbc,
                    "SELECT COUNT(*) FROM tramo_vial WHERE id_tramo = ? AND estado = 'RESTRINGIDO'", goes));
        });
    }

    @Test
    void updateStatusOfAMissingSegmentFails() {
        assertThrows(NotFoundException.class, () -> segmentDao.updateStatus(-1, RoadSegmentStatus.CLOSED));
        assertThrows(NullPointerException.class, () -> segmentDao.updateStatus(1, null));
    }

    @Test
    void trafficLightsAreReadWithEveryColumn() {
        inRolledBackTransaction(jdbc, () -> {
            long[] ids = insertTwoNodes();
            run(jdbc, "INSERT INTO semaforo (id_nodo, codigo, estado_actual, tiempo_ciclo_seg, sincronizado)"
                    + " VALUES (?, 'PRUEBA-A4-1', 'ROJO', 90, 'S')", ids[0]);
            run(jdbc, "INSERT INTO semaforo (id_nodo, codigo) VALUES (?, 'PRUEBA-A4-2')", ids[1]);
            long first = number(jdbc, "SELECT id_semaforo FROM semaforo WHERE codigo = 'PRUEBA-A4-1'");
            long second = number(jdbc, "SELECT id_semaforo FROM semaforo WHERE codigo = 'PRUEBA-A4-2'");
            List<TrafficLight> lights = lightDao.findAll();

            TrafficLight red = withId(lights, TrafficLight::getId, first);
            assertEquals(ids[0], red.getNodeId());
            assertEquals("PRUEBA-A4-1", red.getCode());
            assertEquals(TrafficLightState.RED, red.getCurrentState());
            assertEquals(90, red.getCycleSeconds());
            assertTrue(red.isSynchronizedSignal());
            assertEquals(RecordStatus.ACTIVE, red.getStatus());

            TrafficLight defaults = withId(lights, TrafficLight::getId, second);
            assertEquals(TrafficLightState.GREEN, defaults.getCurrentState(), "default of estado_actual");
            assertNull(defaults.getCycleSeconds(), "a NULL cycle must stay null, not 0");
            assertFalse(defaults.isSynchronizedSignal(), "default of sincronizado");

            assertOrderedById(lights, TrafficLight::getId);
        });
    }

    /**
     * Reads whatever network is loaded (the OpenStreetMap one after K4, or nothing):
     * every row must pass the validations of the entities. Prints the counts and the time,
     * a metric for the defense.
     */
    @Test
    void theWholeLoadedNetworkIsRead() {
        long start = System.nanoTime();
        List<RoadNode> nodes = nodeDao.findAll();
        List<RoadSegment> segments = segmentDao.findAll();
        List<TrafficLight> lights = lightDao.findAll();
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(number(jdbc, "SELECT COUNT(*) FROM nodo_vial"), nodes.size());
        assertEquals(number(jdbc, "SELECT COUNT(*) FROM tramo_vial"), segments.size());
        assertEquals(number(jdbc, "SELECT COUNT(*) FROM semaforo"), lights.size());
        System.out.printf("Red vial leída: %d nodos, %d tramos, %d semáforos en %d ms%n",
                nodes.size(), segments.size(), lights.size(), millis);
    }
}