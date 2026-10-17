
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyTest {

    private static final LocalDateTime REPORTED_AT =
            LocalDateTime.of(2026, 10, 16, 10, 0);

    private static final GeoPoint LOCATION =
            new GeoPoint(10.4631, -73.2532);

    private static final Severity SEVERITY =
            Severity.values()[0];

    private Emergency createEmergency() {
        return new Emergency(
                0, 1, 1, "Calle 16",
                LOCATION, REPORTED_AT, SEVERITY,
                "Accidente de tránsito",
                EmergencyStatus.REPORTED, null);
    }

    @Test
    void createsAValidActiveEmergency() {
        Emergency emergency = createEmergency();

        assertEquals(0, emergency.getId());
        assertEquals(EmergencyStatus.REPORTED, emergency.getStatus());
        assertEquals(LOCATION, emergency.getLocation());
        assertTrue(emergency.isActive());
        assertNull(emergency.getClosedAt());
    }

    @Test
    void reportedCanChangeToAssigned() {
        Emergency emergency = createEmergency();

        emergency.changeStatus(
                EmergencyStatus.ASSIGNED, REPORTED_AT.plusMinutes(5));

        assertEquals(EmergencyStatus.ASSIGNED, emergency.getStatus());
        assertTrue(emergency.isActive());
    }

    @Test
    void reportedCannotChangeDirectlyToOnScene() {
        Emergency emergency = createEmergency();

        assertThrows(BusinessRuleException.class, () ->
                emergency.changeStatus(
                        EmergencyStatus.ON_SCENE,
                        REPORTED_AT.plusMinutes(5)));

        assertEquals(EmergencyStatus.REPORTED, emergency.getStatus());
    }

    @Test
    void closingStoresDateAndDeactivatesEmergency() {
        Emergency emergency = createEmergency();
        LocalDateTime closingTime = REPORTED_AT.plusMinutes(15);

        emergency.changeStatus(EmergencyStatus.CLOSED, closingTime);

        assertEquals(EmergencyStatus.CLOSED, emergency.getStatus());
        assertEquals(closingTime, emergency.getClosedAt());
        assertFalse(emergency.isActive());
    }

    @Test
    void rejectsClosingBeforeReportedAt() {
        Emergency emergency = createEmergency();

        assertThrows(ValidationException.class, () ->
                emergency.changeStatus(
                        EmergencyStatus.CLOSED,
                        REPORTED_AT.minusMinutes(1)));

        assertEquals(EmergencyStatus.REPORTED, emergency.getStatus());
    }

    @Test
    void rejectsClosedEmergencyWithoutClosingDate() {
        assertThrows(ValidationException.class, () ->
                new Emergency(
                        0, 1, 1, "Calle 16",
                        LOCATION, REPORTED_AT, SEVERITY,
                        null, EmergencyStatus.CLOSED, null));
    }

    @Test
    void rejectsReportedEmergencyWithClosingDate() {
        assertThrows(ValidationException.class, () ->
                new Emergency(
                        0, 1, 1, "Calle 16",
                        LOCATION, REPORTED_AT, SEVERITY,
                        null, EmergencyStatus.REPORTED,
                        REPORTED_AT.plusMinutes(10)));
    }

    @Test
    void rejectsInvalidRequiredFields() {
        assertThrows(ValidationException.class, () ->
                new Emergency(
                        0, 0, 1, "Calle 16",
                        LOCATION, REPORTED_AT, SEVERITY,
                        null, EmergencyStatus.REPORTED, null));

        assertThrows(ValidationException.class, () ->
                new Emergency(
                        0, 1, 1, " ",
                        LOCATION, REPORTED_AT, SEVERITY,
                        null, EmergencyStatus.REPORTED, null));

        assertThrows(ValidationException.class, () ->
                new Emergency(
                        0, 1, 1, "Calle 16",
                        null, REPORTED_AT, SEVERITY,
                        null, EmergencyStatus.REPORTED, null));
    }

    @Test
    void assignIdOnlyWorksOnce() {
        Emergency emergency = createEmergency();

        emergency.assignId(15);

        assertEquals(15, emergency.getId());

        assertThrows(IllegalStateException.class,
                () -> emergency.assignId(16));
    }


    @Test
    void newEmergencyMustStartReported() {
        assertThrows(ValidationException.class, () ->
                new Emergency(
                        0, 1, 1, "Calle 16",
                        LOCATION, REPORTED_AT, SEVERITY,
                        "Emergencia de prueba",
                        EmergencyStatus.ASSIGNED, null));
    }

}
