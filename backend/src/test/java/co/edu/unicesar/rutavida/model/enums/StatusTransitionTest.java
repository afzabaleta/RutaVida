package co.edu.unicesar.rutavida.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTransitionTest {

    @Test
    void emergencyFollowsTheNormalFlow() {
        assertTrue(EmergencyStatus.REPORTED.canChangeTo(EmergencyStatus.ASSIGNED));
        assertTrue(EmergencyStatus.ASSIGNED.canChangeTo(EmergencyStatus.EN_ROUTE));
        assertTrue(EmergencyStatus.EN_ROUTE.canChangeTo(EmergencyStatus.ON_SCENE));
        assertTrue(EmergencyStatus.ON_SCENE.canChangeTo(EmergencyStatus.TRANSPORTING));
        assertTrue(EmergencyStatus.TRANSPORTING.canChangeTo(EmergencyStatus.CLOSED));
    }

    @Test
    void emergencyCannotSkipSteps() {
        assertFalse(EmergencyStatus.REPORTED.canChangeTo(EmergencyStatus.ON_SCENE));
        assertFalse(EmergencyStatus.ASSIGNED.canChangeTo(EmergencyStatus.CLOSED));
        assertFalse(EmergencyStatus.EN_ROUTE.canChangeTo(EmergencyStatus.TRANSPORTING));
        assertFalse(EmergencyStatus.REPORTED.canChangeTo(null));
    }

    @Test
    void closedEmergencyIsFinal() {
        for (EmergencyStatus status : EmergencyStatus.values()) {
            assertFalse(EmergencyStatus.CLOSED.canChangeTo(status));
        }
        assertFalse(EmergencyStatus.CLOSED.isActive());
        assertTrue(EmergencyStatus.ON_SCENE.isActive());
    }

    @Test
    void assignmentOpenAndClosedStates() {
        assertTrue(AssignmentStatus.EN_ROUTE.isOpen());
        assertFalse(AssignmentStatus.FINISHED.isOpen());
        assertFalse(AssignmentStatus.CANCELLED.isOpen());
        assertFalse(AssignmentStatus.FINISHED.canChangeTo(AssignmentStatus.EN_ROUTE));
        assertTrue(AssignmentStatus.ASSIGNED.canChangeTo(AssignmentStatus.CANCELLED));
    }

    @Test
    void onlyActiveIncidentsAffectRouting() {
        assertTrue(IncidentStatus.ACTIVE.affectsRouting());
        assertFalse(IncidentStatus.PENDING.affectsRouting());
        assertFalse(IncidentStatus.REJECTED.affectsRouting());
        assertFalse(IncidentStatus.CLOSED.affectsRouting());
    }

    @Test
    void onlyAvailableUnitsCanBeDispatched() {
        assertTrue(VehicleOperationalStatus.AVAILABLE.isDispatchable());
        assertFalse(VehicleOperationalStatus.BUSY.isDispatchable());
        assertFalse(VehicleOperationalStatus.MAINTENANCE.isDispatchable());
    }
}
