package co.edu.unicesar.rutavida.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterPerformanceTest {

    private static final int SIZE = 40;
    private static final int PAIRS = 20;
    private static final double STEP_DEGREES = 0.0005;

    @Test
    void compareAStarAndDijkstra() {
        RoadGraph graph = buildGrid();
        Random random = new Random(42);
        for (int i = 0; i < graph.edgeCount(); i++) {
            double congestion = 1.0 + random.nextDouble() * 2.0;
            graph.update(i, new EdgeState(congestion, false, 0, 1.0, 0));
        }

        AStarRouter router = new AStarRouter();
        CostFunction cost = new EmergencyCost();
        VehicleProfile vehicle = VehicleProfile.AMBULANCE;

        long astarNanos = 0;
        long dijkstraNanos = 0;
        long astarExplored = 0;
        long dijkstraExplored = 0;

        for (int i = 0; i < PAIRS; i++) {
            int src = random.nextInt(graph.nodeCount());
            int dst = random.nextInt(graph.nodeCount());

            long t0 = System.nanoTime();
            RouteResult astar = router.route(graph, src, dst, vehicle, cost, null, true).orElseThrow();
            long t1 = System.nanoTime();
            RouteResult dijkstra = router.route(graph, src, dst, vehicle, cost, null, false).orElseThrow();
            long t2 = System.nanoTime();

            astarNanos += t1 - t0;
            dijkstraNanos += t2 - t1;
            astarExplored += astar.exploredNodes();
            dijkstraExplored += dijkstra.exploredNodes();

            assertEquals(dijkstra.seconds(), astar.seconds(), 0.000001);
            assertTrue(astar.exploredNodes() <= dijkstra.exploredNodes());
        }

        System.out.printf("A*       : %.3f ms, %.1f nodes (average of %d pairs)%n",
                astarNanos / 1e6 / PAIRS, (double) astarExplored / PAIRS, PAIRS);
        System.out.printf("Dijkstra : %.3f ms, %.1f nodes (average of %d pairs)%n",
                dijkstraNanos / 1e6 / PAIRS, (double) dijkstraExplored / PAIRS, PAIRS);
    }

    private static RoadGraph buildGrid() {
        List<Node> nodes = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int index = row * SIZE + col;
                nodes.add(new Node(index, index + 1, 10.40 + row * STEP_DEGREES, -73.30 + col * STEP_DEGREES));
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int index = row * SIZE + col;
                if (col + 1 < SIZE) {
                    twoWay(edges, nodes, index, index + 1);
                }
                if (row + 1 < SIZE) {
                    twoWay(edges, nodes, index, index + SIZE);
                }
            }
        }
        return new RoadGraph(nodes, edges);
    }

    private static void twoWay(List<Edge> edges, List<Node> nodes, int a, int b) {
        double meters = nodes.get(a).distanceMetersTo(nodes.get(b)) * 1.2;
        edges.add(new Edge(edges.size(), 1000 + edges.size(), a, b, meters, 40, "Grid"));
        edges.add(new Edge(edges.size(), 1000 + edges.size(), b, a, meters, 40, "Grid"));
    }
}