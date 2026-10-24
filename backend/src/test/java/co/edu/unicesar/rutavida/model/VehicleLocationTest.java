
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VehicleLocationTest {

    private static final LocalDateTime AT =
            LocalDateTime.of(2026, 10, 23, 8, 3, 15);

    private static final GeoPoint PARQUE_LA_POPA =
            new GeoPoint(10.467172, -73.265099);

    @Test
    void createsASimulatedPosition() {
        VehicleLocation position = new VehicleLocation(
                0, 2, AT, PARQUE_LA_POPA,
                38.5, 270.0, PositionSource.SIMULATION);

        assertEquals(2, position.getVehicleId());
        assertEquals(AT, position.getRecordedAt());
        assertEquals(PARQUE_LA_POPA, position.getLocation());
        assertEquals(38.5, position.getSpeedKmh(), 1e-9);
        assertEquals(270.0, position.getHeadingDegrees(), 1e-9);
        assertTrue(position.isSimulated());
    }

    @Test
    void aGpsPositionIsNotSimulatedAndSpeedAndHeadingAreOptional() {
        VehicleLocation position = new VehicleLocation(
                0, 2, AT, PARQUE_LA_POPA,
                null, null, PositionSource.GPS);

        assertNull(position.getSpeedKmh());
        assertNull(position.getHeadingDegrees());
        assertFalse(position.isSimulated());
    }

    @Test
    void rejectsValuesThatOracleWouldReject() {
        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 2, AT, PARQUE_LA_POPA,
                        -1.0, null, PositionSource.SIMULATION));

        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 2, AT, PARQUE_LA_POPA,
                        null, 360.0, PositionSource.SIMULATION));

        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 2, AT, PARQUE_LA_POPA,
                        null, -0.5, PositionSource.SIMULATION));

        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 0, AT, PARQUE_LA_POPA,
                        null, null, PositionSource.SIMULATION));

        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 2, null, PARQUE_LA_POPA,
                        null, null, PositionSource.SIMULATION));

        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 2, AT, null,
                        null, null, PositionSource.SIMULATION));

        assertThrows(ValidationException.class,
                () -> new VehicleLocation(
                        0, 2, AT, PARQUE_LA_POPA,
                        null, null, null));
    }

    @Test
    void theIdIsAssignedOnce() {
        VehicleLocation position = new VehicleLocation(
                0, 2, AT, PARQUE_LA_POPA,
                null, null, PositionSource.SIMULATION);

        position.assignId(500);

        assertEquals(500, position.getId());

        assertThrows(IllegalStateException.class,
                () -> position.assignId(501));
    }
}
