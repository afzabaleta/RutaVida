package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteBenchmarkTest {

    private static final int SIZE = 30;
    /** About 110 m between corners: the grid is 3,3 km wide, so there are short and long trips. */
    private static final double STEP_DEGREES = 0.001;

    @TempDir
    Path folder;

    /** A two-way grid of SIZE x SIZE corners, like the blocks of a city. */
    private static RoadGraph grid() {
        List<Node> nodes = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int index = row * SIZE + col;
                nodes.add(new Node(index, index + 1, 10.44 + row * STEP_DEGREES, -73.28 + col * STEP_DEGREES));
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int index = row * SIZE + col;
                if (col + 1 < SIZE) {
                    twoWay(edges, nodes, index, index + 1, row % 5 == 0 ? 50 : 30);
                }
                if (row + 1 < SIZE) {
                    twoWay(edges, nodes, index, index + SIZE, col % 5 == 0 ? 50 : 30);
                }
            }
        }
        return new RoadGraph(nodes, edges);
    }

    private static void twoWay(List<Edge> edges, List<Node> nodes, int a, int b, double kmh) {
        double meters = nodes.get(a).distanceMetersTo(nodes.get(b));
        edges.add(new Edge(edges.size(), 1000 + edges.size(), a, b, meters, kmh, "Calle"));
        edges.add(new Edge(edges.size(), 1000 + edges.size(), b, a, meters, kmh, "Calle"));
    }

    @Test
    void aStarFindsTheSameCostExploringFewerNodes() {
        List<RouteBenchmark.Trip> trips = RouteBenchmark.measure(grid(), 40, new Random(RouteBenchmark.SEED));

        assertEquals(40, trips.size());
        for (RouteBenchmark.Trip trip : trips) {
            assertTrue(trip.sameCost(), "same cost: " + trip);
            assertTrue(trip.aStarNodes() <= trip.dijkstraNodes(), "fewer nodes: " + trip);
            assertTrue(trip.meters() > 0 && trip.seconds() > 0, trip.toString());
            assertTrue(trip.aStarMs() >= 0 && trip.dijkstraMs() >= 0, trip.toString());
        }
        RouteBenchmark.Summary all = RouteBenchmark.summarize(trips).get(3);
        assertTrue(all.nodeRatio() > 1.2, "A* explores fewer nodes: " + all.nodeRatio());
    }

    @Test
    void theSameSeedMeasuresTheSameTrips() {
        RoadGraph graph = grid();
        List<RouteBenchmark.Trip> first = RouteBenchmark.measure(graph, 10, new Random(7));
        List<RouteBenchmark.Trip> second = RouteBenchmark.measure(graph, 10, new Random(7));

        for (int i = 0; i < 10; i++) {
            assertEquals(first.get(i).from(), second.get(i).from());
            assertEquals(first.get(i).to(), second.get(i).to());
            assertEquals(first.get(i).aStarNodes(), second.get(i).aStarNodes());
            assertEquals(first.get(i).dijkstraNodes(), second.get(i).dijkstraNodes());
        }
    }

    @Test
    void theGroupsSplitTheTripsByLength() {
        List<RouteBenchmark.Trip> trips = List.of(
                new RouteBenchmark.Trip(1, 2, 1_500, 120, 0.2, 0.8, 50, 400, true),
                new RouteBenchmark.Trip(3, 4, 3_000, 240, 0.4, 1.6, 100, 900, true),
                new RouteBenchmark.Trip(5, 6, 6_000, 480, 1.0, 3.0, 300, 1_800, true));

        List<RouteBenchmark.Summary> groups = RouteBenchmark.summarize(trips);

        assertEquals(List.of(1, 1, 1, 3), groups.stream().map(RouteBenchmark.Summary::trips).toList());
        RouteBenchmark.Summary all = groups.get(3);
        assertEquals(3.5, all.averageKm(), 1e-9);
        assertEquals(150, all.aStarNodes(), 1e-9);
        assertEquals(1_033.33, all.dijkstraNodes(), 0.01);
        assertEquals(3, all.sameCost());
        assertEquals(8.0, groups.get(0).nodeRatio(), 1e-9);
        assertEquals(4.0, groups.get(0).speedUp(), 1e-9);
    }

    @Test
    void anEmptyGroupIsZero() {
        List<RouteBenchmark.Summary> groups = RouteBenchmark.summarize(
                List.of(new RouteBenchmark.Trip(1, 2, 500, 60, 0.1, 0.2, 10, 30, true)));

        RouteBenchmark.Summary longTrips = groups.get(2);
        assertEquals(0, longTrips.trips());
        assertEquals(0, longTrips.nodeRatio(), 1e-9);
        assertEquals(0, longTrips.speedUp(), 1e-9);
    }

    @Test
    void theCsvHasOneLinePerTripWithDecimalPoint() throws IOException {
        Path file = folder.resolve("metricas").resolve("a-estrella-vs-dijkstra.csv");
        RouteBenchmark.writeCsv(List.of(
                new RouteBenchmark.Trip(10, 20, 2_345.67, 210.5, 0.25, 1.5, 120, 980, true)), file);

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("# RutaVida - A* contra Dijkstra"), lines.get(0));
        assertEquals("viaje;nodo_origen;nodo_destino;metros;segundos;astar_ms;dijkstra_ms;"
                + "astar_nodos;dijkstra_nodos;mismo_costo", lines.get(1));
        assertEquals("1;10;20;2345.7;210.5;0.2500;1.5000;120;980;SI", lines.get(2));
    }

    @Test
    void atLeastOneTrip() {
        assertThrows(IllegalArgumentException.class, () -> RouteBenchmark.measure(grid(), 0, new Random(1)));
    }
}
