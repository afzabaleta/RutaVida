
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteTest {

    private static final LocalDateTime AT =
            LocalDateTime.of(2026, 10, 23, 8, 1);

    private static final List<RouteSegment> THREE_SEGMENTS = List.of(
            new RouteSegment(1, 101, 40.0, 1.0, 1),
            new RouteSegment(2, 102, 65.5, 2.3, 0),
            new RouteSegment(3, 103, 30.0, 1.0, 2)
    );

    private static Route route(Long hospitalId,
                               List<RouteSegment> segments) {
        return new Route(
                0, 4, 2, hospitalId,
                RouteType.MAIN,
                1.85, 2.26, 135.5,
                AT, true, RouteStatus.PROPOSED, segments
        );
    }

    @Test
    void aRouteToTheEmergencyKeepsItsSegmentsInOrder() {
        Route route = route(null, THREE_SEGMENTS);

        assertEquals(4, route.getEmergencyId());
        assertEquals(RouteType.MAIN, route.getType());
        assertTrue(route.isRecommended());
        assertFalse(route.isToHospital());
        assertEquals(3, route.getSegments().size());
        assertEquals(102, route.getSegments().get(1).segmentId());
    }

    @Test
    void aRouteWithAHospitalIsATransfer() {
        assertTrue(route(3L, THREE_SEGMENTS).isToHospital());

        assertThrows(ValidationException.class,
                () -> route(0L, THREE_SEGMENTS));
    }

    @Test
    void usesSegmentTellsWhetherARoadSegmentIsOnTheRoute() {
        Route route = route(null, THREE_SEGMENTS);

        assertTrue(route.usesSegment(103));
        assertFalse(route.usesSegment(999));
    }

    @Test
    void segmentsMustBeNumberedOneTwoThreeWithoutGaps() {
        assertThrows(ValidationException.class,
                () -> route(null, List.of()));

        assertThrows(ValidationException.class,
                () -> route(null, null));

        assertThrows(ValidationException.class,
                () -> route(null, List.of(
                        new RouteSegment(1, 101, null, null, null),
                        new RouteSegment(3, 102, null, null, null)
                )));

        assertThrows(ValidationException.class,
                () -> route(null, List.of(
                        new RouteSegment(2, 101, null, null, null)
                )));
    }

    @Test
    void theSegmentsCannotBeChangedFromOutside() {
        List<RouteSegment> mine = new ArrayList<>(THREE_SEGMENTS);
        Route route = route(null, mine);

        mine.clear();

        assertEquals(3, route.getSegments().size(),
                "the route keeps its own copy");

        assertThrows(UnsupportedOperationException.class,
                () -> route.getSegments().add(
                        new RouteSegment(4, 104, null, null, null)
                ));
    }

    @Test
    void rejectsValuesThatOracleWouldReject() {
        assertThrows(ValidationException.class,
                () -> new Route(
                        0, 4, 2, null, RouteType.MAIN,
                        -0.1, 2, 1,
                        AT, false, RouteStatus.PROPOSED,
                        THREE_SEGMENTS));

        assertThrows(ValidationException.class,
                () -> new Route(
                        0, 4, 2, null, RouteType.MAIN,
                        1, -1, 1,
                        AT, false, RouteStatus.PROPOSED,
                        THREE_SEGMENTS));

        assertThrows(ValidationException.class,
                () -> new Route(
                        0, 4, 2, null, null,
                        1, 2, 1,
                        AT, false, RouteStatus.PROPOSED,
                        THREE_SEGMENTS));

        assertThrows(ValidationException.class,
                () -> new Route(
                        0, 0, 2, null, RouteType.MAIN,
                        1, 2, 1,
                        AT, false, RouteStatus.PROPOSED,
                        THREE_SEGMENTS));

        assertThrows(ValidationException.class,
                () -> new Route(
                        0, 4, 2, null, RouteType.ALTERNATIVE,
                        1, 2, 1,
                        null, false, RouteStatus.PROPOSED,
                        THREE_SEGMENTS));
    }

    @Test
    void theIdIsAssignedOnce() {
        Route route = route(null, THREE_SEGMENTS);

        route.assignId(12);

        assertEquals(12, route.getId());

        assertThrows(IllegalStateException.class,
                () -> route.assignId(13));
    }
}
