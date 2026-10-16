
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyVehicleTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 16, 10, 0);

    private static final GeoPoint LOCATION =
            new GeoPoint(10.4631, -73.2532);

    private EmergencyVehicle createVehicle(
            VehicleOperationalStatus status) {
        return new EmergencyVehicle(
                0, 1, 1, " amb-01 ", null,
                status, null, null);
    }

    @Test
    void createsAValidVehicleWithoutLocation() {
        EmergencyVehicle vehicle =
                createVehicle(VehicleOperationalStatus.AVAILABLE);

        assertEquals(0, vehicle.getId());
        assertEquals(1, vehicle.getVehicleTypeId());
        assertEquals(1, vehicle.getStationId());
        assertEquals("AMB-01", vehicle.getUnitCode());
        assertNull(vehicle.getLocation());
        assertNull(vehicle.getLastLocationAt());
        assertTrue(vehicle.isDispatchable());
    }

    @Test
    void normalizesPlateToUppercaseWithoutSpaces() {
        EmergencyVehicle vehicle = new EmergencyVehicle(
                0, 1, 1, "AMB-01", " abc 123 ",
                VehicleOperationalStatus.AVAILABLE,
                null, null);

        assertEquals("ABC123", vehicle.getPlate());
    }

    @Test
    void rejectsLocationWithoutTimestamp() {
        assertThrows(ValidationException.class, () ->
                new EmergencyVehicle(
                        0, 1, 1, "AMB-01", null,
                        VehicleOperationalStatus.AVAILABLE,
                        LOCATION, null));
    }

    @Test
    void moveToUpdatesLocationAndTimestamp() {
        EmergencyVehicle vehicle =
                createVehicle(VehicleOperationalStatus.AVAILABLE);

        vehicle.moveTo(LOCATION, NOW);

        assertEquals(LOCATION, vehicle.getLocation());
        assertEquals(NOW, vehicle.getLastLocationAt());
    }

    @Test
    void moveToRejectsOlderTimestamp() {
        EmergencyVehicle vehicle = new EmergencyVehicle(
                0, 1, 1, "AMB-01", null,
                VehicleOperationalStatus.AVAILABLE,
                LOCATION, NOW);

        GeoPoint next = new GeoPoint(10.47, -73.26);

        assertThrows(ValidationException.class, () ->
                vehicle.moveTo(next, NOW.minusMinutes(1)));

        assertEquals(LOCATION, vehicle.getLocation());
    }

    @Test
    void dispatchabilityDependsOnOperationalStatus() {
        EmergencyVehicle vehicle =
                createVehicle(VehicleOperationalStatus.AVAILABLE);

        assertTrue(vehicle.isDispatchable());

        vehicle.changeStatus(VehicleOperationalStatus.BUSY);

        assertFalse(vehicle.isDispatchable());
    }

    @Test
    void rejectsInvalidRequiredFields() {
        assertThrows(ValidationException.class, () ->
                new EmergencyVehicle(
                        0, 0, 1, "AMB-01", null,
                        VehicleOperationalStatus.AVAILABLE,
                        null, null));

        assertThrows(ValidationException.class, () ->
                new EmergencyVehicle(
                        0, 1, 0, "AMB-01", null,
                        VehicleOperationalStatus.AVAILABLE,
                        null, null));

        assertThrows(ValidationException.class, () ->
                new EmergencyVehicle(
                        0, 1, 1, " ", null,
                        VehicleOperationalStatus.AVAILABLE,
                        null, null));

        assertThrows(ValidationException.class, () ->
                new EmergencyVehicle(
                        0, 1, 1, "AMB-01", null,
                        null, null, null));
    }

    @Test
    void moveToRejectsNullArguments() {
        EmergencyVehicle vehicle =
                createVehicle(VehicleOperationalStatus.AVAILABLE);

        assertThrows(ValidationException.class,
                () -> vehicle.moveTo(null, NOW));

        assertThrows(ValidationException.class,
                () -> vehicle.moveTo(LOCATION, null));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        EmergencyVehicle vehicle =
                createVehicle(VehicleOperationalStatus.AVAILABLE);

        vehicle.assignId(10);

        assertEquals(10, vehicle.getId());

        assertThrows(IllegalStateException.class,
                () -> vehicle.assignId(11));
    }
}
