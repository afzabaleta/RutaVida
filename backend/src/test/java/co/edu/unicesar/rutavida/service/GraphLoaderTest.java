package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadSegmentDAO;
import co.edu.unicesar.rutavida.graph.AStarRouter;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadNode;
import co.edu.unicesar.rutavida.model.RoadSegment;
import co.edu.unicesar.rutavida.model.TrafficLight;
import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraphLoader with fake DAOs, without Oracle. Test network (made up, not real data):
 * <pre>
 *   4 ---(105, one way 3->4)--- 3        9 (inactive)    7 === 8   (island)
 *   |                         / |
 *  106 CERRADO 4->1   110 RESTRINGIDO 1->3
 *   |                 /         |
 *   1 ===(101/102)=== 2 ===(103/104)=== 3
 * </pre>
 */
class GraphLoaderTest {

    private static RoadNode node(long id, double lat, double lon, RecordStatus status) {
        return new RoadNode(id, null, null, new GeoPoint(lat, lon), NodeType.INTERSECTION, status);
    }

    private static RoadSegment segment(long id, long from, long to, RoadSegmentStatus status) {
        return new RoadSegment(id, from, to, null, "Via " + id, 110.0, 30, status);
    }

    private static final List<RoadNode> NODES = List.of(
            node(1, 10.4600, -73.2600, RecordStatus.ACTIVE),
            node(2, 10.4600, -73.2590, RecordStatus.ACTIVE),
            node(3, 10.4610, -73.2590, RecordStatus.ACTIVE),
            node(4, 10.4610, -73.2600, RecordStatus.ACTIVE),
            node(7, 10.4700, -73.2500, RecordStatus.ACTIVE),
            node(8, 10.4701, -73.2500, RecordStatus.ACTIVE),
            node(9, 10.4620, -73.2600, RecordStatus.INACTIVE));

    private static final List<RoadSegment> SEGMENTS = List.of(
            segment(101, 1, 2, RoadSegmentStatus.OPEN),
            segment(102, 2, 1, RoadSegmentStatus.OPEN),
            segment(103, 2, 3, RoadSegmentStatus.OPEN),
            segment(104, 3, 2, RoadSegmentStatus.OPEN),
            segment(105, 3, 4, RoadSegmentStatus.OPEN),
            segment(106, 4, 1, RoadSegmentStatus.CLOSED),
            segment(107, 1, 9, RoadSegmentStatus.OPEN),
            segment(108, 7, 8, RoadSegmentStatus.OPEN),
            segment(109, 8, 7, RoadSegmentStatus.OPEN),
            segment(110, 1, 3, RoadSegmentStatus.RESTRICTED));

    private static final List<TrafficLight> LIGHTS = List.of(
            new TrafficLight(1, 2, "SEM-2", TrafficLightState.GREEN, 80, false, RecordStatus.ACTIVE),
            new TrafficLight(2, 3, "SEM-3", TrafficLightState.RED, null, false, RecordStatus.ACTIVE),
            new TrafficLight(3, 4, "SEM-4", TrafficLightState.OUT_OF_SERVICE, 60, false, RecordStatus.ACTIVE),
            new TrafficLight(4, 1, "SEM-1", TrafficLightState.GREEN, 60, false, RecordStatus.INACTIVE));

    /** RoadSegmentDAO has two methods, so it cannot be a lambda. */
    private static RoadSegmentDAO segments(List<RoadSegment> rows) {
        return new RoadSegmentDAO() {
            @Override
            public List<RoadSegment> findAll() {
                return rows;
            }

            @Override
            public void updateStatus(long id, RoadSegmentStatus status) {
                throw new UnsupportedOperationException("GraphLoader only reads");
            }
        };
    }

    private static RoadGraph load() {
        return new GraphLoader(() -> NODES, segments(SEGMENTS), () -> LIGHTS).load();
    }

    private static Edge edge(RoadGraph graph, long segmentId) {
        int index = graph.edgeIndexOf(segmentId);
        assertTrue(index >= 0, "segment " + segmentId + " must be in the graph");
        return graph.edge(index);
    }

    @Test
    void nodesAndEdgesKeepTheirOracleIds() {
        RoadGraph graph = load();

        assertEquals(4, graph.nodeCount());
        assertEquals(7, graph.edgeCount());
        Edge first = edge(graph, 101);
        assertEquals(1, graph.node(first.from()).dbId());
        assertEquals(2, graph.node(first.to()).dbId());
        assertEquals(110.0, first.lengthM(), 1e-9);
        assertEquals(30, first.freeFlowKmh(), 1e-9);
        assertEquals("Via 101", first.name());
        assertEquals(10.4610, graph.node(graph.nodeIndexOf(3)).lat(), 1e-9);
    }

    @Test
    void inactiveNodesTheirSegmentsAndSmallIslandsAreLeftOut() {
        RoadGraph graph = load();

        assertEquals(-1, graph.nodeIndexOf(9), "inactive node");
        assertEquals(-1, graph.edgeIndexOf(107), "segment to an inactive node");
        assertEquals(-1, graph.nodeIndexOf(7), "island not connected to the main network");
        assertEquals(-1, graph.edgeIndexOf(108));
    }

    @Test
    void aClosedSegmentStaysButClosedAndARestrictedOneIsOpen() {
        RoadGraph graph = load();

        assertTrue(graph.state(edge(graph, 106).index()).closed());
        assertFalse(graph.state(edge(graph, 110).index()).closed());
        assertFalse(graph.state(edge(graph, 101).index()).closed());
    }

    @Test
    void workingTrafficLightsAddTheirWaitToTheSegmentsThatArrive() {
        RoadGraph graph = load();

        assertEquals(10.0, graph.state(edge(graph, 101).index()).signalDelaySec(), 1e-9, "cycle 80 s -> 80 / 8");
        assertEquals(10.0, graph.state(edge(graph, 104).index()).signalDelaySec(), 1e-9);
        assertEquals(11.25, graph.state(edge(graph, 103).index()).signalDelaySec(), 1e-9, "no cycle -> 90 / 8");
        assertEquals(11.25, graph.state(edge(graph, 110).index()).signalDelaySec(), 1e-9);
        assertEquals(0.0, graph.state(edge(graph, 105).index()).signalDelaySec(), 1e-9, "out of service");
        assertEquals(0.0, graph.state(edge(graph, 102).index()).signalDelaySec(), 1e-9, "inactive light");
    }

    @Test
    void theRouteEngineWorksOnTheLoadedGraphAndAvoidsTheClosedSegment() {
        RoadGraph graph = load();
        AStarRouter router = new AStarRouter();
        int one = graph.nodeIndexOf(1);
        int four = graph.nodeIndexOf(4);

        Optional<RouteResult> toFour = router.route(graph, one, four, VehicleProfile.AMBULANCE,
                new EmergencyCost(), null, true);
        assertTrue(toFour.isPresent());
        List<Integer> path = toFour.get().edgeIds();
        int lastEdge = path.get(path.size() - 1);
        assertEquals(edge(graph, 105).index(), lastEdge, "it arrives at 4 by the one-way 3 -> 4");

        // From 4 the only exit is 106, which is closed: there is no route back to 1.
        assertTrue(router.route(graph, four, one, VehicleProfile.AMBULANCE, new EmergencyCost(), null, true)
                .isEmpty());
    }

    @Test
    void withoutANetworkTheMessageSaysWhatToRun() {
        GraphLoader empty = new GraphLoader(() -> NODES, segments(List.of()), List::of);

        IllegalStateException error = assertThrows(IllegalStateException.class, empty::load);

        assertTrue(error.getMessage().contains("05_red_vial_valledupar.sql"));
    }

    @Test
    void theExpectedWaitIsAnEighthOfTheCycle() {
        assertEquals(10.0, GraphLoader.expectedWaitSeconds(80), 1e-9);
        assertEquals(11.25, GraphLoader.expectedWaitSeconds(null), 1e-9);
    }
}