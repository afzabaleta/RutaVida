package co.edu.unicesar.rutavida.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStarRouterTest {

    private final AStarRouter router = new AStarRouter();
    private final VehicleProfile ambulance = VehicleProfile.AMBULANCE;
    private final CostFunction emergency = new EmergencyCost();

    private RouteResult find(RoadGraph graph, CostFunction cost, boolean heuristic) {
        return router.route(graph, 0, 2, ambulance, cost, null, heuristic).orElseThrow();
    }

    @Test
    void freeGraph() {
        RouteResult result = find(SampleGraph.sixNodes(), emergency, true);
        assertEquals(List.of(0, 2), result.edgeIds());
        assertEquals(132.0, result.seconds(), 0.001);
        assertEquals(1100.0, result.meters(), 0.001);
    }

    @Test
    void astarMatchesDijkstra() {
        RoadGraph graph = SampleGraph.sixNodes();
        RouteResult astar = find(graph, emergency, true);
        RouteResult dijkstra = find(graph, emergency, false);
        assertEquals(dijkstra.seconds(), astar.seconds(), 0.000001);
        assertTrue(astar.exploredNodes() <= dijkstra.exploredNodes());
    }

    @Test
    void closedEdgeIsAvoided() {
        RoadGraph graph = SampleGraph.sixNodes();
        graph.update(2, new EdgeState(1.0, true, 0, 1.0, 0));
        RouteResult result = find(graph, emergency, true);
        assertFalse(result.edgeIds().contains(2));
        assertEquals(List.of(8, 4, 6, 13), result.edgeIds());
        assertEquals(166.8, result.seconds(), 0.001);
    }

    @Test
    void fastestIsNotTheShortest() {
        RoadGraph graph = SampleGraph.sixNodes();
        graph.update(0, new EdgeState(3.0, false, 0, 1.0, 0));
        graph.update(2, new EdgeState(3.0, false, 0, 1.0, 0));
        RouteResult fastest = find(graph, emergency, true);
        RouteResult shortest = find(graph, new DistanceOnlyCost(), true);
        assertEquals(List.of(8, 4, 6, 13), fastest.edgeIds());
        assertEquals(166.8, fastest.seconds(), 0.001);
        assertEquals(List.of(0, 2), shortest.edgeIds());
        assertEquals(1100.0, shortest.meters(), 0.001);
    }

    @Test
    void noPathReturnsEmpty() {
        RoadGraph graph = new RoadGraph(
                List.of(new Node(0, 1, 10.46, -73.26), new Node(1, 2, 10.47, -73.25)), List.of());
        Optional<RouteResult> result = router.route(graph, 0, 1, ambulance, emergency, null, true);
        assertTrue(result.isEmpty());
    }

    @Test
    void sourceEqualsDestination() {
        RouteResult result = router.route(SampleGraph.sixNodes(), 3, 3, ambulance, emergency, null, true)
                .orElseThrow();
        assertTrue(result.edgeIds().isEmpty());
        assertEquals(0.0, result.seconds(), 0.001);
    }
}