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
}