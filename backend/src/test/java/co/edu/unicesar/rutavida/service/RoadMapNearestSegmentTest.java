package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.NearbySegment;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RoadMapService.nearestSegment (docs/api.md 9.4) on the 3 x 3 test city of DispatchTestSupport,
 * plus a one-way street: clicking on a street gives its segment and the reverse one.
 */
class RoadMapNearestSegmentTest {

    private final RoadGraph graph = new DispatchTestSupport().graph;

    /** The DAO answers from the graph itself, like Oracle does from TRAMO_VIAL and NODO_VIAL. */
    private List<SegmentView> views(RoadGraph roads, java.util.Collection<Long> ids) {
        List<SegmentView> views = new ArrayList<>();
        for (long id : ids) {
            Edge edge = roads.edge(roads.edgeIndexOf(id));
            Node from = roads.node(edge.from());
            Node to = roads.node(edge.to());
            views.add(new SegmentView(id, edge.name(), new GeoPoint(from.lat(), from.lon()),
                    new GeoPoint(to.lat(), to.lon())));
        }
        return views;
    }

    @Test
    void aClickOnAStreetGivesItsSegmentAndTheOtherDirection() {
        RoadMapService service = new RoadMapService(ids -> views(graph, ids), () -> graph);
        GeoPoint middle = new GeoPoint(10.46205, -73.257);  // 5 m north of the street (1,1)-(1,2)

        NearbySegment nearest = service.nearestSegment(middle);

        assertEquals("Calle de prueba", nearest.streetName());
        assertTrue(nearest.distanceMeters() < 10, "about 5.6 m: " + nearest.distanceMeters());
        assertEquals(DispatchTestSupport.at(1, 1), nearest.from());
        assertEquals(DispatchTestSupport.at(1, 2), nearest.to());
        long reverse = nearest.reverseSegmentId();
        Edge back = graph.edge(graph.edgeIndexOf(reverse));
        assertEquals(graph.edge(graph.edgeIndexOf(nearest.segmentId())).from(), back.to(), "the same two corners");
    }

    @Test
    void aOneWayStreetHasNoReverse() {
        RoadGraph oneWay = new RoadGraph(
                List.of(new Node(0, 1, 10.460, -73.260), new Node(1, 2, 10.460, -73.258)),
                List.of(new Edge(0, 77, 0, 1, 219, 30, "Calle de un sentido")));
        RoadMapService service = new RoadMapService(ids -> views(oneWay, ids), () -> oneWay);
        NearbySegment nearest = service.nearestSegment(new GeoPoint(10.4600, -73.259));
        assertEquals(77, nearest.segmentId());
        assertNull(nearest.reverseSegmentId());
    }

    @Test
    void farFromEveryStreetOrWithoutTheGraphThereIsNoSegment() {
        RoadMapService service = new RoadMapService(ids -> views(graph, ids), () -> graph);
        BusinessRuleException far = assertThrows(BusinessRuleException.class,
                () -> service.nearestSegment(new GeoPoint(10.50, -73.20)));
        assertTrue(far.getMessage().contains("150 m"));

        RoadMapService withoutGraph = new RoadMapService(ids -> List.of());
        assertThrows(BusinessRuleException.class, () -> withoutGraph.nearestSegment(new GeoPoint(10.46, -73.26)));
    }
}
