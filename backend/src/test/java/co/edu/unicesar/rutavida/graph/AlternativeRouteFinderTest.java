package co.edu.unicesar.rutavida.graph;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternativeRouteFinderTest {

    private final VehicleProfile ambulance = VehicleProfile.AMBULANCE;
    private final AlternativeRouteFinder finder =
            new AlternativeRouteFinder(new AStarRouter(), new EmergencyCost(), 0.8, 1.5);

    @Test
    void returnsTwoRoutesOnTheSampleGraph() {
        List<RouteResult> routes = finder.find(SampleGraph.sixNodes(), 0, 2, ambulance, 3);
        assertEquals(2, routes.size());
        assertEquals(List.of(0, 2), routes.get(0).edgeIds());
        assertEquals(132.0, routes.get(0).seconds(), 0.001);
        assertEquals(List.of(8, 4, 6, 13), routes.get(1).edgeIds());
        assertEquals(166.8, routes.get(1).seconds(), 0.001);
    }

    @Test
    void respectsRuleRN08() {
        List<RouteResult> routes = finder.find(SampleGraph.sixNodes(), 0, 2, ambulance, 3);
        double best = routes.get(0).seconds();
        for (int i = 0; i < routes.size(); i++) {
            assertTrue(routes.get(i).seconds() <= best * 1.5 + 0.001);
            for (int j = 0; j < routes.size(); j++) {
                if (i != j) {
                    Set<Integer> other = new HashSet<>(routes.get(j).edgeIds());
                    long common = routes.get(i).edgeIds().stream().filter(other::contains).count();
                    assertTrue((double) common / routes.get(i).edgeIds().size() <= 0.8);
                }
            }
        }
    }

    @Test
    void doesNotModifyTheGraph() {
        RoadGraph graph = SampleGraph.sixNodes();
        long version = graph.version();
        finder.find(graph, 0, 2, ambulance, 3);
        assertEquals(version, graph.version());
        for (int i = 0; i < graph.edgeCount(); i++) {
            assertEquals(EdgeState.free(), graph.state(i));
        }
    }
}
