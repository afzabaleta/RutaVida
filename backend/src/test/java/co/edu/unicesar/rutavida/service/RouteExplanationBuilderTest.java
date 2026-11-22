package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-made graph (test data only, fictitious streets):
 * <pre>
 *   C(2) ── 1500 m ── D(3)
 *    |                 |
 *  1500 m            1000 m      plus a direct A → D of 1800 m (edge 4)
 *    |                 |
 *   A(0) ── 1000 m ── B(1)
 * </pre>
 * Via B is shorter but congested; via C is free; the direct one has an incident.
 */
class RouteExplanationBuilderTest {

    private final RouteExplanationBuilder builder = new RouteExplanationBuilder();
    private final RoadGraph graph = graph();

    /** Recommended: A → C → D, 3,0 km in 6,0 min. */
    private final RouteResult viaC = new RouteResult(List.of(2, 3), 360, 3000, 0);
    /** A → B → D, 2,0 km in 7,3 min. */
    private final RouteResult viaB = new RouteResult(List.of(0, 1), 438, 2000, 0);
    /** A → D direct, 1,8 km in 6,0 min. */
    private final RouteResult direct = new RouteResult(List.of(4), 360, 1800, 0);

    private static RoadGraph graph() {
        List<Node> nodes = List.of(
                new Node(0, 1, 10.4600, -73.2600),
                new Node(1, 2, 10.4600, -73.2550),
                new Node(2, 3, 10.4650, -73.2600),
                new Node(3, 4, 10.4650, -73.2550));
        List<Edge> edges = List.of(
                new Edge(0, 501, 0, 1, 1000, 40, "Calle de prueba"),
                new Edge(1, 502, 1, 3, 1000, 40, "Carrera de prueba"),
                new Edge(2, 503, 0, 2, 1500, 60, "Avenida de prueba"),
                new Edge(3, 504, 2, 3, 1500, 60, "Avenida de prueba"),
                new Edge(4, 505, 0, 3, 1800, 40, "Diagonal de prueba"));
        RoadGraph graph = new RoadGraph(nodes, edges);
        graph.update(0, new EdgeState(2.0, false, 30, 1.0, 0));
        graph.update(1, new EdgeState(1.6, false, 20, 1.0, 0));
        graph.update(2, new EdgeState(1.0, false, 15, 1.0, 0));
        graph.update(4, new EdgeState(1.0, false, 0, 1.5, 0));
        return graph;
    }

    @Test
    void theRecommendedRouteSaysWhy() {
        assertEquals("Ruta recomendada: es la más rápida, 6,0 min y 3,0 km. Tiene 1 semáforo y 0 tramos congestionados.",
                builder.explain(viaC, viaC, graph));
    }

    @Test
    void aShorterButCongestedAlternativeSaysWhatItCosts() {
        assertEquals("Alternativa: 1,3 min más lenta que la recomendada (7,3 min, 2,0 km). Es 1,0 km más corta."
                        + " Tiene 2 tramos congestionados más. Tiene 1 semáforo más.",
                builder.explain(viaB, viaC, graph));
    }

    @Test
    void anAlternativeWithTheSameTimeMentionsTheIncident() {
        assertEquals("Alternativa: tarda lo mismo que la recomendada (6,0 min, 1,8 km). Es 1,2 km más corta."
                        + " Tiene 1 semáforo menos. Pasa por 1 tramo con incidente más.",
                builder.explain(direct, viaC, graph));
    }

    @Test
    void aFasterOptionSaysWhatItAvoids() {
        assertEquals("Alternativa: 1,3 min más rápida que la recomendada (6,0 min, 3,0 km). Es 1,0 km más larga."
                        + " Evita 2 tramos congestionados. Tiene 1 semáforo menos.",
                builder.explain(viaC, viaB, graph));
    }

    @Test
    void aRecommendedRouteWithAnIncidentSaysSo() {
        assertEquals("Ruta recomendada: es la más rápida, 6,0 min y 1,8 km. Tiene 0 semáforos y 0 tramos congestionados."
                        + " Pasa por 1 tramo con incidente.",
                builder.explain(direct, direct, graph));
    }

    @Test
    void sameOriginAndDestination() {
        RouteResult empty = new RouteResult(List.of(), 0, 0, 0);
        assertEquals("El origen y el destino están en el mismo punto.", builder.explain(empty, empty, graph));
    }

    @Test
    void toOptionFillsEverythingTheMapNeeds() {
        RouteOption main = builder.toOption(1, viaC, viaC, graph);
        assertEquals(1, main.rank());
        assertEquals(RouteType.MAIN, main.type());
        assertEquals(List.of(new GeoPoint(10.4600, -73.2600), new GeoPoint(10.4650, -73.2600),
                new GeoPoint(10.4650, -73.2550)), main.path());
        assertEquals(List.of(503L, 504L), main.segmentIds());
        assertEquals(3.0, main.distanceKm(), 0.0001);
        assertEquals(6.0, main.estimatedMinutes(), 0.0001);
        assertEquals(1, main.trafficLights());
        assertEquals(0, main.congestedSegments());

        RouteOption second = builder.toOption(2, viaB, viaC, graph);
        assertEquals(RouteType.ALTERNATIVE, second.type());
        assertEquals(2, second.congestedSegments());
        assertEquals(2, second.trafficLights());
    }

    @Test
    void neverModifiesTheGraph() {
        long version = graph.version();
        builder.toOption(1, viaC, viaC, graph);
        builder.explain(viaB, viaC, graph);
        assertEquals(version, graph.version());
    }
}
