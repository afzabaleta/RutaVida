package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test data only: an invented emergency at Parque La Popa. */
class EmergencyServiceTest {

    private static final LocalDateTime REPORTED = LocalDateTime.of(2026, 10, 28, 10, 0);
    private final EmergencyService service = new EmergencyService();

    private static Emergency emergency(EmergencyStatus status) {
        return new Emergency(0, 1, 1, "Parque La Popa", new GeoPoint(10.467172, -73.265099),
                REPORTED, Severity.HIGH, "Prueba", status, null);
    }

    @Test
    void aValidNewEmergencyPasses() {
        service.validateNew(emergency(EmergencyStatus.REPORTED));
        assertTrue(service.isActive(emergency(EmergencyStatus.REPORTED)));
    }

    @Test
    void aNewEmergencyMustStartReportedAndHaveItsData() {
        assertThrows(ValidationException.class, () -> service.validateNew(emergency(EmergencyStatus.ASSIGNED)));
        Emergency noAddress = new Emergency(0, 1, 1, " ", new GeoPoint(10.46, -73.26),
                REPORTED, Severity.HIGH, null, EmergencyStatus.REPORTED, null);
        assertThrows(ValidationException.class, () -> service.validateNew(noAddress));
        Emergency badPoint = new Emergency(0, 1, 1, "Calle 1", new GeoPoint(95, -73.26),
                REPORTED, Severity.HIGH, null, EmergencyStatus.REPORTED, null);
        assertThrows(ValidationException.class, () -> service.validateNew(badPoint));
        Emergency noType = new Emergency(0, 0, 1, "Calle 1", new GeoPoint(10.46, -73.26),
                REPORTED, Severity.HIGH, null, EmergencyStatus.REPORTED, null);
        assertThrows(ValidationException.class, () -> service.validateNew(noType));
    }

    @Test
    void theNormalFlowIsAllowedAndClosingRecordsTheTime() {
        Emergency e = emergency(EmergencyStatus.REPORTED);
        service.changeStatus(e, EmergencyStatus.ASSIGNED, REPORTED.plusMinutes(1));
        service.changeStatus(e, EmergencyStatus.EN_ROUTE, REPORTED.plusMinutes(2));
        service.changeStatus(e, EmergencyStatus.ON_SCENE, REPORTED.plusMinutes(8));
        assertNull(e.getClosedAt());
        service.changeStatus(e, EmergencyStatus.CLOSED, REPORTED.plusMinutes(30));
        assertEquals(EmergencyStatus.CLOSED, e.getStatus());
        assertEquals(REPORTED.plusMinutes(30), e.getClosedAt());
        assertFalse(service.isActive(e));
    }

    @Test
    void stepsCannotBeSkippedAndAClosedEmergencyIsFinal() {
        Emergency e = emergency(EmergencyStatus.REPORTED);
        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(e, EmergencyStatus.ON_SCENE, REPORTED.plusMinutes(1)));
        service.changeStatus(e, EmergencyStatus.CLOSED, REPORTED.plusMinutes(1));
        for (EmergencyStatus next : EmergencyStatus.values()) {
            assertFalse(service.canChange(EmergencyStatus.CLOSED, next));
        }
    }

    @Test
    void itCannotCloseBeforeTheReport() {
        Emergency e = emergency(EmergencyStatus.REPORTED);
        assertThrows(ValidationException.class,
                () -> service.changeStatus(e, EmergencyStatus.CLOSED, REPORTED.minusMinutes(1)));
        assertEquals(EmergencyStatus.REPORTED, e.getStatus());
    }
}
