
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteSegmentTest {

    @Test
    void keepsTheEstimatesOfTheStep() {
        RouteSegment step = new RouteSegment(2, 102, 65.5, 2.3, 1);

        assertEquals(2, step.order());
        assertEquals(102, step.segmentId());
        assertEquals(65.5, step.estimatedSeconds(), 1e-9);
        assertEquals(2.3, step.congestionFactor(), 1e-9);
        assertEquals(1, step.trafficLightCount());
    }

    @Test
    void theEstimatesAreOptionalAndItComparesByValue() {
        RouteSegment step = new RouteSegment(1, 101, null, null, null);

        assertNull(step.estimatedSeconds());
        assertEquals(
                new RouteSegment(1, 101, null, null, null),
                step);
    }

    @Test
    void rejectsValuesThatOracleWouldReject() {
        assertThrows(ValidationException.class,
                () -> new RouteSegment(0, 101, null, null, null));

        assertThrows(ValidationException.class,
                () -> new RouteSegment(1, 0, null, null, null));

        assertThrows(ValidationException.class,
                () -> new RouteSegment(1, 101, -1.0, null, null));

        assertThrows(ValidationException.class,
                () -> new RouteSegment(1, 101, null, 0.9, null));

        assertThrows(ValidationException.class,
                () -> new RouteSegment(1, 101, null, null, -1));
    }
}
