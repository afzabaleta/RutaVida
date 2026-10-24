
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TrafficReadingTest {

    private static final LocalDateTime AT =
            LocalDateTime.of(2026, 10, 22, 7, 30);

    @Test
    void createsASimulatedReading() {
        TrafficReading reading = new TrafficReading(
                0, 9, AT,
                TrafficLevel.CONGESTED,
                12.5, 2.4,
                TrafficSource.SIMULATION
        );

        assertEquals(9, reading.getSegmentId());
        assertEquals(AT, reading.getMeasuredAt());
        assertEquals(TrafficLevel.CONGESTED, reading.getLevel());
        assertEquals(12.5, reading.getAverageSpeedKmh(), 1e-9);
        assertEquals(2.4, reading.getCongestionFactor(), 1e-9);
        assertTrue(reading.isSimulated());
    }

    @Test
    void aReadingFromASensorIsNotSimulatedAndSpeedIsOptional() {
        TrafficReading reading = new TrafficReading(
                0, 9, AT,
                TrafficLevel.FLUID,
                null, 1.0,
                TrafficSource.SENSOR
        );

        assertNull(reading.getAverageSpeedKmh());
        assertFalse(reading.isSimulated());
    }

    @Test
    void rejectsValuesThatOracleWouldReject() {
        assertThrows(ValidationException.class, () ->
                new TrafficReading(0, 9, AT,
                        TrafficLevel.FLUID, null, 0.99,
                        TrafficSource.SIMULATION));

        assertThrows(ValidationException.class, () ->
                new TrafficReading(0, 9, AT,
                        TrafficLevel.FLUID, -1.0, 1.0,
                        TrafficSource.SIMULATION));

        assertThrows(ValidationException.class, () ->
                new TrafficReading(0, 0, AT,
                        TrafficLevel.FLUID, null, 1.0,
                        TrafficSource.SIMULATION));

        assertThrows(ValidationException.class, () ->
                new TrafficReading(0, 9, null,
                        TrafficLevel.FLUID, null, 1.0,
                        TrafficSource.SIMULATION));

        assertThrows(ValidationException.class, () ->
                new TrafficReading(0, 9, AT,
                        null, null, 1.0,
                        TrafficSource.SIMULATION));

        assertThrows(ValidationException.class, () ->
                new TrafficReading(0, 9, AT,
                        TrafficLevel.FLUID, null, 1.0,
                        null));
    }
}
