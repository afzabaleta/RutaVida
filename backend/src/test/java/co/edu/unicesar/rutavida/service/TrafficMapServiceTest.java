package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.dto.TrafficSegmentView;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** TrafficMapService with in-memory DAOs and two EXAMPLE segments: 413 congested, 414 moderate. */
class TrafficMapServiceTest {

    private static final GeoPoint A = new GeoPoint(10.468901, -73.258004);
    private static final GeoPoint B = new GeoPoint(10.468377, -73.260112);

    private final LocalDateTime now = LocalDateTime.now(CLOCK);
    private final TrafficSegmentView calle16 = new TrafficSegmentView(413, "Calle 16 (EJEMPLO)",
            TrafficLevel.CONGESTED, 2.6, now.minusMinutes(3), TrafficSource.SIMULATION, A, B);
    private final TrafficSegmentView carrera9 = new TrafficSegmentView(414, "Carrera 9 (EJEMPLO)",
            TrafficLevel.MODERATE, 1.3, now.minusMinutes(9), TrafficSource.SIMULATION, B, A);
    private final List<LocalDateTime> asked = new ArrayList<>();

    private final TrafficMapService service = new TrafficMapService(
            since -> {
                asked.add(since);
                return List.of(calle16, carrera9);
            },
            ids -> ids.contains(413L) ? List.of(new SegmentView(413, "Calle 16 (EJEMPLO)", A, B)) : List.of(),
            CLOCK);

    @Test
    void theLayerShowsTheLast15MinutesAndCanKeepOnlyOneLevel() {
        assertEquals(List.of(calle16, carrera9), service.current(null));
        assertEquals(List.of(now.minus(TrafficMapService.WINDOW)), asked, "the same window as the dashboard");

        assertEquals(List.of(calle16), service.current(TrafficLevel.CONGESTED));
        assertEquals(List.of(carrera9), service.current(TrafficLevel.MODERATE));
    }

    @Test
    void freeFlowIsNotALayer() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service.current(TrafficLevel.FLUID));
        assertEquals(TrafficMapService.ONLY_CONGESTION, error.getMessage());
    }

    @Test
    void aForcedSegmentLooksLikeTheMapWillPaintIt() {
        TrafficSegmentView forced = service.forced(413, TrafficLevel.MODERATE, TrafficSource.SIMULATION);

        assertEquals(new TrafficSegmentView(413, "Calle 16 (EJEMPLO)", TrafficLevel.MODERATE,
                TrafficRecorder.congestionOf(TrafficLevel.MODERATE), now, TrafficSource.SIMULATION, A, B), forced);
        assertThrows(NotFoundException.class, () -> service.forced(999, TrafficLevel.CONGESTED,
                TrafficSource.SIMULATION));
    }
}
