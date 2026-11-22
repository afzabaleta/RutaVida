package co.edu.unicesar.rutavida.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphTest {

    @Test
    void buildsTheSampleGraph() {
        RoadGraph graph = SampleGraph.sixNodes();
        assertEquals(6, graph.nodeCount());
        assertEquals(14, graph.edgeCount());
        assertEquals(2, graph.outgoing(0).size());
        assertEquals(3, graph.outgoing(1).size());
    }

    @Test
    void everyEdgeStartsFree() {
        RoadGraph graph = SampleGraph.sixNodes();
        for (int i = 0; i < graph.edgeCount(); i++) {
            assertEquals(EdgeState.free(), graph.state(i));
        }
    }

    @Test
    void updateReplacesTheStateAndIncreasesTheVersion() {
        RoadGraph graph = SampleGraph.sixNodes();
        long before = graph.version();
        EdgeState jam = new EdgeState(3.0, false, 20, 1.0, 1000);
        graph.update(0, jam);
        assertEquals(jam, graph.state(0));
        assertTrue(graph.version() > before);
    }

    @Test
    void findsIndexesByOracleId() {
        RoadGraph graph = SampleGraph.sixNodes();
        assertEquals(0, graph.nodeIndexOf(1));
        assertEquals(-1, graph.nodeIndexOf(999));
        assertEquals(0, graph.edgeIndexOf(100));
    }

    @Test
    void nearestNodeIsTheClosestIntersection() {
        RoadGraph graph = SampleGraph.sixNodes();
        assertEquals(4, graph.nearestNode(10.4649, -73.2551));
    }

    @Test
    void outgoingEdgesCannotBeModified() {
        RoadGraph graph = SampleGraph.sixNodes();
        assertThrows(UnsupportedOperationException.class, () -> graph.outgoing(0).clear());
    }

    @Test
    void rejectsAnEdgeToAMissingNode() {
        List<Node> nodes = List.of(new Node(0, 1, 10.46, -73.26));
        List<Edge> edges = List.of(new Edge(0, 1, 0, 5, 100, 30, "X"));
        assertThrows(IllegalArgumentException.class, () -> new RoadGraph(nodes, edges));
    }

    @Test
    void edgeStateRejectsCongestionBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeState(0.5, false, 0, 1, 0));
        assertFalse(EdgeState.free().closed());
    }

    @Test
    void edgeLengthsAreNeverShorterThanTheStraightLine() {
        RoadGraph graph = SampleGraph.sixNodes();
        for (int i = 0; i < graph.edgeCount(); i++) {
            Edge edge = graph.edge(i);
            double straight = graph.node(edge.from()).distanceMetersTo(graph.node(edge.to()));
            assertTrue(edge.lengthM() >= straight, "edge " + i + " is shorter than the straight line");
        }
    }

    /** A two-way street A-B of about 219 m to the east and a one-way street A-C to the north. */
    private static RoadGraph streets() {
        List<Node> nodes = List.of(new Node(0, 10, 10.460, -73.260), new Node(1, 11, 10.460, -73.258),
                new Node(2, 12, 10.462, -73.260));
        List<Edge> edges = List.of(new Edge(0, 100, 0, 1, 219, 40, "Calle A-B"),
                new Edge(1, 101, 1, 0, 219, 40, "Calle A-B"),
                new Edge(2, 102, 0, 2, 221, 40, "Carrera A-C"));
        return new RoadGraph(nodes, edges);
    }

    @Test
    void theNearestEdgeIsMeasuredToTheLineNotToTheNodes() {
        RoadGraph graph = streets();
        // About 11 m north of the middle of A-B: its nodes are 110 m away, the line only 11.
        int edge = graph.nearestEdge(10.4601, -73.259);
        assertEquals(100, graph.edge(edge).dbId());
        assertEquals(11.1, graph.distanceToEdgeMeters(edge, 10.4601, -73.259), 0.5);
        // 5 m east of the middle of A-C.
        assertEquals(102, graph.edge(graph.nearestEdge(10.461, -73.25995)).dbId());
        // Beyond the end B the distance is to B itself.
        assertEquals(109.5, graph.distanceToEdgeMeters(0, 10.460, -73.257), 1.0);
    }

    @Test
    void aTwoWayStreetHasItsReverseAndAOneWayStreetDoesNot() {
        RoadGraph graph = streets();
        assertEquals(1, graph.reverseEdge(0));
        assertEquals(0, graph.reverseEdge(1));
        assertEquals(-1, graph.reverseEdge(2));
        assertEquals(-1, new RoadGraph(List.of(new Node(0, 1, 10.46, -73.26)), List.of()).nearestEdge(10.46, -73.26));
    }
}
