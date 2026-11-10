package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadMapDAO;
import co.edu.unicesar.rutavida.dto.IncidentView;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
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

/** RoadMapService with a fake RoadMapDAO that knows only segment 413. Does not need Oracle. */
class RoadMapServiceTest {

    private static final SegmentView CALLE_16 = new SegmentView(413, "Calle 16",
            new GeoPoint(10.468901, -73.258004), new GeoPoint(10.468377, -73.260112));

    private final List<Collection<Long>> asked = new ArrayList<>();
    private final RoadMapDAO dao = ids -> {
        asked.add(Set.copyOf(ids));
        return ids.contains(413L) ? List.of(CALLE_16) : List.of();
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

    @Test
    void noIncidentsNoQuery() {
        assertTrue(service.describe(List.of()).isEmpty());
        assertTrue(asked.isEmpty());
    }
}