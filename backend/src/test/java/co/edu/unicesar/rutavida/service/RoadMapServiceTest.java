package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadMapDAO;
import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.DispatchedMission;
import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.MissionRoute;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RoadMapService with a fake RoadMapDAO that knows only segments 413 and 414. Does not need Oracle. */
class RoadMapServiceTest {

    private static final SegmentView CALLE_16 = new SegmentView(413, "Calle 16",
            new GeoPoint(10.468901, -73.258004), new GeoPoint(10.468377, -73.260112));
    private static final SegmentView CALLE_16_B = new SegmentView(414, "Calle 16",
            new GeoPoint(10.468377, -73.260112), new GeoPoint(10.467172, -73.265099));

    private final List<Collection<Long>> asked = new ArrayList<>();
    private final RoadMapDAO dao = ids -> {
        asked.add(Set.copyOf(ids));
        return List.of(CALLE_16, CALLE_16_B).stream().filter(segment -> ids.contains(segment.segmentId())).toList();
    };
    private final RoadMapService service = new RoadMapService(dao);

    private static RoadIncident incident(long id, long segmentId) {
        return new RoadIncident(id, segmentId, IncidentType.ACCIDENT, Severity.HIGH, false, "Prueba",
                LocalDateTime.now(CLOCK), null, IncidentStatus.PENDING, IncidentOrigin.OPERATOR);
    }

    @Test
    void eachIncidentGetsItsSegmentWithOneQuery() {
        List<IncidentView> views = service.describe(List.of(incident(1, 413), incident(2, 413), incident(3, 999)));

        assertEquals(List.of(1L, 2L, 3L), views.stream().map(IncidentView::id).toList(), "same order");
        assertEquals(CALLE_16, views.get(0).segment());
        assertEquals(CALLE_16, views.get(1).segment());
        assertNull(views.get(2).segment(), "a segment that is no longer in TRAMO_VIAL");
        assertEquals(List.of(Set.of(413L, 999L)), asked, "one query, each segment once");
    }

    @Test
    void theViewKeepsTheFieldsOfTheIncident() {
        IncidentView view = service.describe(incident(7, 413));

        assertEquals(7, view.id());
        assertEquals(413, view.segmentId());
        assertEquals(IncidentType.ACCIDENT, view.type());
        assertEquals(Severity.HIGH, view.severity());
        assertEquals(IncidentStatus.PENDING, view.status());
        assertEquals(IncidentOrigin.OPERATOR, view.origin());
        assertEquals("Prueba", view.description());
        assertEquals("Calle 16", view.segment().streetName());
    }

    private static Route route(long id, boolean recommended, double minutes, double km, RouteSegment... steps) {
        return new Route(id, 15, 1, null, recommended ? RouteType.MAIN : RouteType.ALTERNATIVE, km, minutes,
                minutes * 60, LocalDateTime.now(CLOCK), recommended,
                recommended ? RouteStatus.ACTIVE : RouteStatus.PROPOSED, List.of(steps));
    }

    @Test
    void theRoutesOfAMissionComeRecommendedFirstWithTheirLineAndCounts() {
        Route alternative = route(31, false, 8.0, 3.0, new RouteSegment(1, 414, 60.0, 1.0, 0));
        Route main = route(30, true, 6.9, 2.8,
                new RouteSegment(2, 414, 90.0, 1.6, 0),
                new RouteSegment(1, 413, 30.0, 1.0, 1));

        List<MissionRoute> views = service.describeRoutes(List.of(alternative, main));

        MissionRoute first = views.get(0);
        assertEquals(30, first.routeId(), "the recommended one first");
        assertEquals(1, first.rank());
        assertEquals(RouteStatus.ACTIVE, first.status());
        assertEquals(List.of(413L, 414L), first.segmentIds(), "in the order of RUTA_TRAMO");
        assertEquals(List.of(CALLE_16.from(), CALLE_16.to(), CALLE_16_B.to()), first.path());
        assertEquals(1, first.trafficLights());
        assertEquals(1, first.congestedSegments(), "1.6 is congested");
        assertEquals("Ruta recomendada: 6,9 min y 2,8 km con el tráfico de cuando se calculó."
                + " Tiene 1 semáforo y 1 tramo congestionado.", first.explanation());

        MissionRoute second = views.get(1);
        assertEquals(2, second.rank());
        assertEquals(RouteType.ALTERNATIVE, second.type());
        assertEquals("Alternativa: 1,1 min más lenta que la recomendada (8,0 min, 3,0 km).", second.explanation());
        assertEquals(List.of(Set.of(413L, 414L)), asked, "one query for all the routes");
    }

    @Test
    void aSegmentNoLongerInTheNetworkIsLeftOutOfTheLineButKeepsItsId() {
        MissionRoute view = service.describeRoutes(List.of(route(30, true, 1.0, 0.4,
                new RouteSegment(1, 413, 30.0, 1.0, 0), new RouteSegment(2, 999, 30.0, 1.0, 0)))).get(0);

        assertEquals(List.of(413L, 999L), view.segmentIds());
        assertEquals(List.of(CALLE_16.from(), CALLE_16.to()), view.path());
    }

    @Test
    void aDispatchKeepsItsAssignment() {
        EmergencyAssignment assignment = new EmergencyAssignment(8, 15, 1, 3, LocalDateTime.now(CLOCK),
                null, null, AssignmentStatus.ASSIGNED);

        DispatchedMission view = service.describeDispatch(new DispatchResult(assignment,
                List.of(route(30, true, 1.0, 0.2, new RouteSegment(1, 413, 30.0, 1.0, 0)))));

        assertEquals(assignment, view.assignment());
        assertEquals(30, view.routes().get(0).routeId());
        assertTrue(service.describeRoutes(List.of()).isEmpty());
    }

    @Test
    void noIncidentsNoQuery() {
        assertTrue(service.describe(List.of()).isEmpty());
        assertTrue(asked.isEmpty());
    }
}