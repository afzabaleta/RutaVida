package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test data only: invented unit and assignment. */
class DispatchServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 28, 10, 0);
    private final DispatchService service = new DispatchService();

    private static EmergencyAssignment assignment() {
        return new EmergencyAssignment(0, 1, 2, 3, T, null, null, AssignmentStatus.ASSIGNED);
    }

    private static EmergencyVehicle vehicle(VehicleOperationalStatus status) {
        return new EmergencyVehicle(1, 1, 1, "AMB-01", "ABC123", status, null, null);
    }

    @Test
    void onlyAvailableUnitsCanBeDispatched() {
        assertTrue(service.isDispatchable(vehicle(VehicleOperationalStatus.AVAILABLE)));
        assertFalse(service.isDispatchable(vehicle(VehicleOperationalStatus.BUSY)));
        assertFalse(service.isDispatchable(vehicle(VehicleOperationalStatus.MAINTENANCE)));
    }

    @Test
    void aNewAssignmentNeedsItsReferencesAndStartsAssigned() {
        service.validateNew(assignment());
        assertThrows(ValidationException.class, () -> service.validateNew(
                new EmergencyAssignment(0, 0, 2, 3, T, null, null, AssignmentStatus.ASSIGNED)));
        assertThrows(ValidationException.class, () -> service.validateNew(
                new EmergencyAssignment(0, 1, 2, 3, T, null, null, AssignmentStatus.EN_ROUTE)));
    }

    @Test
    void theMissionRecordsTheDepartureAndTheArrival() {
        EmergencyAssignment a = assignment();
        assertTrue(service.isOpen(a));
        service.changeStatus(a, AssignmentStatus.EN_ROUTE, T.plusMinutes(1));
        assertEquals(T.plusMinutes(1), a.getDepartedAt());
        assertNull(a.getArrivedAt());
        service.changeStatus(a, AssignmentStatus.ON_SCENE, T.plusMinutes(7));
        assertEquals(T.plusMinutes(7), a.getArrivedAt());
        service.changeStatus(a, AssignmentStatus.FINISHED, T.plusMinutes(40));
        assertFalse(service.isOpen(a));
    }

    @Test
    void changesNotAllowedOrBackInTimeAreRejected() {
        EmergencyAssignment a = assignment();
        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(a, AssignmentStatus.ON_SCENE, T.plusMinutes(1)));
        assertThrows(ValidationException.class,
                () -> service.changeStatus(a, AssignmentStatus.EN_ROUTE, T.minusMinutes(1)));
        service.changeStatus(a, AssignmentStatus.CANCELLED, T.plusMinutes(2));
        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(a, AssignmentStatus.EN_ROUTE, T.plusMinutes(3)));
    }

    @Test
    void aUnitMovesForwardInTimeOnly() {
        EmergencyVehicle v = vehicle(VehicleOperationalStatus.BUSY);
        service.moveVehicle(v, new GeoPoint(10.46, -73.26), T);
        assertEquals(T, v.getLastLocationAt());
        assertThrows(ValidationException.class,
                () -> service.moveVehicle(v, new GeoPoint(10.47, -73.25), T.minusSeconds(5)));
        assertThrows(ValidationException.class,
                () -> service.moveVehicle(v, new GeoPoint(100, -73.25), T.plusSeconds(5)));
        assertEquals(10.46, v.getLastLocation().latitude());
    }
}
