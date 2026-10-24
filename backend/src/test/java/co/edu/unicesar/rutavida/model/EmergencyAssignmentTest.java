
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyAssignmentTest {

    private static final LocalDateTime ASSIGNED_AT =
            LocalDateTime.of(2026, 10, 23, 8, 0);

    private static EmergencyAssignment newAssignment() {
        return new EmergencyAssignment(
                0, 4, 2, 7, ASSIGNED_AT, null, null,
                AssignmentStatus.ASSIGNED);
    }

    @Test
    void aNewAssignmentIsOpenAndHasNotLeft() {
        EmergencyAssignment assignment = newAssignment();

        assertEquals(4, assignment.getEmergencyId());
        assertEquals(2, assignment.getVehicleId());
        assertEquals(7, assignment.getCoordinatorUserId());
        assertEquals(AssignmentStatus.ASSIGNED, assignment.getStatus());
        assertTrue(assignment.isOpen());
        assertNull(assignment.getDepartedAt());
        assertNull(assignment.getTimeToArrival());
    }

    @Test
    void theMissionRecordsTheDepartureAndTheArrival() {
        EmergencyAssignment assignment = newAssignment();

        assignment.changeStatus(
                AssignmentStatus.EN_ROUTE, ASSIGNED_AT.plusMinutes(2));
        assertEquals(
                ASSIGNED_AT.plusMinutes(2), assignment.getDepartedAt());
        assertNull(assignment.getArrivedAt());

        assignment.changeStatus(
                AssignmentStatus.ON_SCENE, ASSIGNED_AT.plusMinutes(9));
        assertEquals(
                ASSIGNED_AT.plusMinutes(9), assignment.getArrivedAt());
        assertEquals(
                Duration.ofMinutes(9), assignment.getTimeToArrival());

        assignment.changeStatus(
                AssignmentStatus.TRANSPORTING, ASSIGNED_AT.plusMinutes(20));
        assignment.changeStatus(
                AssignmentStatus.FINISHED, ASSIGNED_AT.plusMinutes(35));

        assertEquals(AssignmentStatus.FINISHED, assignment.getStatus());
        assertFalse(assignment.isOpen());
        assertEquals(
                ASSIGNED_AT.plusMinutes(9), assignment.getArrivedAt(),
                "later changes keep the arrival");
    }

    @Test
    void changesNotAllowedByTheStatusAreRejected() {
        EmergencyAssignment assignment = newAssignment();

        assertThrows(BusinessRuleException.class,
                () -> assignment.changeStatus(
                        AssignmentStatus.ON_SCENE,
                        ASSIGNED_AT.plusMinutes(5)));
        assertEquals(
                AssignmentStatus.ASSIGNED, assignment.getStatus());

        assignment.changeStatus(
                AssignmentStatus.CANCELLED, ASSIGNED_AT.plusMinutes(1));
        assertFalse(assignment.isOpen());

        assertThrows(BusinessRuleException.class,
                () -> assignment.changeStatus(
                        AssignmentStatus.EN_ROUTE,
                        ASSIGNED_AT.plusMinutes(2)));
    }

    @Test
    void timesCannotGoBackwards() {
        EmergencyAssignment assignment = newAssignment();

        assertThrows(ValidationException.class,
                () -> assignment.changeStatus(
                        AssignmentStatus.EN_ROUTE,
                        ASSIGNED_AT.minusMinutes(1)));

        assertEquals(
                AssignmentStatus.ASSIGNED, assignment.getStatus(),
                "a rejected change leaves it as it was");

        assignment.changeStatus(
                AssignmentStatus.EN_ROUTE, ASSIGNED_AT.plusMinutes(5));

        assertThrows(ValidationException.class,
                () -> assignment.changeStatus(
                        AssignmentStatus.ON_SCENE,
                        ASSIGNED_AT.plusMinutes(4)));

        assertThrows(ValidationException.class,
                () -> assignment.changeStatus(
                        AssignmentStatus.ON_SCENE, null));
    }

    @Test
    void aRowReadFromOracleMustMatchItsStatus() {
        LocalDateTime left = ASSIGNED_AT.plusMinutes(2);
        LocalDateTime arrived = ASSIGNED_AT.plusMinutes(9);

        EmergencyAssignment onScene = new EmergencyAssignment(
                15, 4, 2, 7, ASSIGNED_AT, left, arrived,
                AssignmentStatus.ON_SCENE);

        assertEquals(15, onScene.getId());

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        15, 4, 2, 7, ASSIGNED_AT,
                        null, null, AssignmentStatus.EN_ROUTE));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        15, 4, 2, 7, ASSIGNED_AT,
                        left, null, AssignmentStatus.ON_SCENE));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        15, 4, 2, 7, ASSIGNED_AT,
                        left, null, AssignmentStatus.ASSIGNED));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        15, 4, 2, 7, ASSIGNED_AT,
                        null, arrived, AssignmentStatus.CANCELLED));

        EmergencyAssignment cancelledOnTheWay = new EmergencyAssignment(
                16, 4, 2, 7, ASSIGNED_AT, left, null,
                AssignmentStatus.CANCELLED);

        assertNull(cancelledOnTheWay.getTimeToArrival());
    }

    @Test
    void rejectsMissingReferences() {
        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        0, 0, 2, 7, ASSIGNED_AT,
                        null, null, AssignmentStatus.ASSIGNED));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        0, 4, 0, 7, ASSIGNED_AT,
                        null, null, AssignmentStatus.ASSIGNED));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        0, 4, 2, 0, ASSIGNED_AT,
                        null, null, AssignmentStatus.ASSIGNED));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        0, 4, 2, 7, null,
                        null, null, AssignmentStatus.ASSIGNED));

        assertThrows(ValidationException.class,
                () -> new EmergencyAssignment(
                        0, 4, 2, 7, ASSIGNED_AT,
                        null, null, null));
    }

    @Test
    void theIdIsAssignedOnceAndDefinesEquality() {
        EmergencyAssignment assignment = newAssignment();

        assertNotEquals(
                assignment, newAssignment(),
                "unsaved assignments are compared by identity");

        assignment.assignId(30);
        assertEquals(30, assignment.getId());

        assertThrows(IllegalStateException.class,
                () -> assignment.assignId(31));

        assertEquals(
                new EmergencyAssignment(
                        30, 4, 2, 7, ASSIGNED_AT,
                        null, null, AssignmentStatus.ASSIGNED),
                assignment);
    }
}
