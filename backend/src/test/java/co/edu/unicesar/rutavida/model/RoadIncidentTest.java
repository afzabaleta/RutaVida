
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RoadIncidentTest {

    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 10, 22, 8, 0);

    private RoadIncident newIncident() {
        return new RoadIncident(
                0, 12, IncidentType.ACCIDENT, Severity.HIGH,
                false, "Choque en la Calle 16", STARTED, null,
                IncidentStatus.PENDING, IncidentOrigin.OPERATOR
        );
    }

    @Test
    void aNewIncidentIsPendingAndDoesNotAffectRoutes() {
        RoadIncident incident = newIncident();

        assertEquals(IncidentStatus.PENDING, incident.getStatus());
        assertEquals("Choque en la Calle 16", incident.getDescription());
        assertNull(incident.getEndedAt());
        assertFalse(incident.affectsRouting());
        assertFalse(incident.blocksTraffic());
    }

    @Test
    void aNewIncidentMustStartPending() {
        assertThrows(ValidationException.class, () ->
                new RoadIncident(
                        0, 12, IncidentType.CLOSURE, Severity.HIGH,
                        true, null, STARTED, null,
                        IncidentStatus.ACTIVE, IncidentOrigin.SIMULATION
                ));
    }

    @Test
    void activatingDecidesWhetherTheSegmentIsClosed() {
        RoadIncident penalizes = newIncident();
        penalizes.activate(false);

        assertEquals(IncidentStatus.ACTIVE, penalizes.getStatus());
        assertTrue(penalizes.affectsRouting());
        assertFalse(penalizes.blocksTraffic());

        RoadIncident closes = newIncident();
        closes.activate(true);

        assertTrue(closes.isBlocksSegment());
        assertTrue(closes.blocksTraffic());
    }

    @Test
    void rejectingEndsAPendingIncident() {
        RoadIncident incident = newIncident();
        incident.reject(STARTED.plusMinutes(10));

        assertEquals(IncidentStatus.REJECTED, incident.getStatus());
        assertEquals(STARTED.plusMinutes(10), incident.getEndedAt());
        assertFalse(incident.affectsRouting());

        assertThrows(BusinessRuleException.class, () ->
                incident.activate(true));
    }

    @Test
    void onlyAnActiveIncidentCanBeClosed() {
        RoadIncident incident = newIncident();

        assertThrows(BusinessRuleException.class, () ->
                incident.close(STARTED.plusHours(1)));

        incident.activate(true);
        incident.close(STARTED.plusHours(1));

        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertEquals(STARTED.plusHours(1), incident.getEndedAt());
        assertFalse(incident.blocksTraffic());

        assertThrows(BusinessRuleException.class, () ->
                incident.reject(STARTED.plusHours(2)));
    }

    @Test
    void theEndCannotBeBeforeTheStart() {
        RoadIncident incident = newIncident();
        incident.activate(false);

        assertThrows(ValidationException.class, () ->
                incident.close(STARTED.minusMinutes(1)));

        assertThrows(ValidationException.class, () ->
                incident.close(null));

        assertEquals(IncidentStatus.ACTIVE, incident.getStatus());
    }

    @Test
    void anIncidentReadFromOracleMustHaveAConsistentEndDate() {
        RoadIncident closed = new RoadIncident(
                5, 12, IncidentType.ROADWORK, Severity.MEDIUM,
                true, null, STARTED, STARTED.plusDays(1),
                IncidentStatus.CLOSED, IncidentOrigin.OPERATOR
        );

        assertEquals(5, closed.getId());

        assertThrows(ValidationException.class, () ->
                new RoadIncident(
                        5, 12, IncidentType.ROADWORK, Severity.MEDIUM,
                        true, null, STARTED, null,
                        IncidentStatus.CLOSED, IncidentOrigin.OPERATOR
                ));

        assertThrows(ValidationException.class, () ->
                new RoadIncident(
                        5, 12, IncidentType.ROADWORK, Severity.MEDIUM,
                        true, null, STARTED, STARTED.plusDays(1),
                        IncidentStatus.ACTIVE, IncidentOrigin.OPERATOR
                ));

        assertThrows(ValidationException.class, () ->
                new RoadIncident(
                        0, 12, IncidentType.ROADWORK, Severity.MEDIUM,
                        true, "x".repeat(301), STARTED, null,
                        IncidentStatus.PENDING, IncidentOrigin.CITIZEN_QR
                ));
    }
}
