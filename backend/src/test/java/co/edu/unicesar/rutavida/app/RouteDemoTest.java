package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo on a hand-made grid of 6 x 6 two-way streets around Parque La Popa (test data
 * only: fictitious streets, all at 40 km/h). The real network is used by RouteDemo.main.
 */
class RouteDemoTest {

    private static final GeoPoint STATION = new GeoPoint(10.468580, -73.257590);
    private static final GeoPoint HOSPITAL = new GeoPoint(10.469330, -73.254520);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
    private static final int SIDE = 6;

    /** Grid from (10.4640, -73.2670) to (10.4715, -73.2520): La Popa, the station and the hospital fall inside. */
    private static RoadGraph grid() {
        List<Node> nodes = new ArrayList<>();
        for (int row = 0; row < SIDE; row++) {
            for (int col = 0; col < SIDE; col++) {
                nodes.add(new Node(nodes.size(), nodes.size() + 1L, 10.4640 + row * 0.0015, -73.2670 + col * 0.0030));
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (int row = 0; row < SIDE; row++) {
            for (int col = 0; col < SIDE; col++) {
                int here = row * SIDE + col;
                if (col + 1 < SIDE) {
                    twoWay(edges, nodes, here, here + 1, "Calle de prueba " + row);
                }
                if (row + 1 < SIDE) {
                    twoWay(edges, nodes, here, here + SIDE, "Carrera de prueba " + col);
                }
            }
        }
        return new RoadGraph(nodes, edges);
    }

    private static void twoWay(List<Edge> edges, List<Node> nodes, int a, int b, String name) {
        double meters = nodes.get(a).distanceMetersTo(nodes.get(b));
        edges.add(new Edge(edges.size(), 1000L + edges.size(), a, b, meters, 40, name));
        edges.add(new Edge(edges.size(), 1000L + edges.size(), b, a, meters, 40, name));
    }

    private static String run(RoadGraph graph, RouteGeoJsonWriter[] map) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        map[0] = RouteDemo.simulate(graph, STATION, HOSPITAL, CLOCK,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void theDemoCalculatesRecalculatesAndFillsTheMap() {
        RouteGeoJsonWriter[] map = new RouteGeoJsonWriter[1];
        String out = run(grid(), map);

        assertTrue(out.contains("1) Estación -> emergencia"), out);
        assertTrue(out.contains("   Ruta 1: "), out);
        assertTrue(out.contains("2) A* exploró "), out);
        assertTrue(out.contains("Mismo tiempo de viaje: sí."), out);
        assertTrue(out.contains("3) SIMULACION: la unidad lleva "), out);
        assertTrue(out.contains("4) Emergencia -> HOSPITAL ROSARIO PUMAREJO DE LOPEZ"), out);

        String json = map[0].toJson();
        for (String kind : List.of("estacion", "emergencia", "hospital", "unidad", "congestion", "ruta")) {
            assertTrue(json.contains("\"kind\":\"" + kind + "\""), "missing " + kind);
        }
        assertTrue(json.contains("\"leg\":\"ida\",\"scenario\":\"inicial\",\"rank\":1"));
        assertTrue(json.contains("\"leg\":\"ida\",\"scenario\":\"congestion-simulada\",\"rank\":1"));
        assertTrue(json.contains("\"leg\":\"traslado\",\"scenario\":\"congestion-simulada\",\"rank\":1"));
        assertTrue(json.contains("SIMULACION"), "simulated data is labeled");
    }

    @Test
    void theJamMakesTheEngineChangeRoute() {
        RoadGraph graph = grid();
        RouteGeoJsonWriter[] map = new RouteGeoJsonWriter[1];
        String out = run(graph, map);

        assertTrue(out.contains("El motor cambia de ruta y ahorra "), out);
        long jammed = 0;
        for (int i = 0; i < graph.edgeCount(); i++) {
            if (graph.state(i).congestion() == RouteDemo.JAM) {
                jammed++;
                assertEquals(CLOCK.millis(), graph.state(i).updatedAtMs());
            }
        }
        assertTrue(jammed > 0, "the demo congests segments of the best route");
    }

    @Test
    void withoutARouteTheMessageSaysWhy() {
        // Only one segment, from La Popa to the station: the station cannot reach La Popa.
        RoadGraph graph = new RoadGraph(
                List.of(new Node(0, 1, STATION.latitude(), STATION.longitude()),
                        new Node(1, 2, RouteDemo.EMERGENCY.latitude(), RouteDemo.EMERGENCY.longitude())),
                List.of(new Edge(0, 10, 1, 0, 830, 40, "Calle de prueba")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> run(graph, new RouteGeoJsonWriter[1]));
        assertEquals("No hay ruta desde la estación hasta la emergencia", error.getMessage());
    }
}