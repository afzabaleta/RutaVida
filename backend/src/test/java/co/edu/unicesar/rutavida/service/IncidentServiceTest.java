package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test data only: an invented incident on segment 7. */
class IncidentServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 8, 0);
    private final IncidentService service = new IncidentService();

    private static RoadIncident pending() {
        return new RoadIncident(0, 7, IncidentType.ACCIDENT, Severity.MEDIUM, false, "Choque simple",
                START, null, IncidentStatus.PENDING, IncidentOrigin.OPERATOR);
    }

    @Test
    void aNewIncidentStartsPendingAndDoesNotAffectRoutes() {
        RoadIncident incident = pending();
        service.validateNew(incident);
        assertFalse(service.affectsRouting(incident));
        RoadIncident active = new RoadIncident(0, 7, IncidentType.ACCIDENT, Severity.MEDIUM, false, null,
                START, null, IncidentStatus.ACTIVE, IncidentOrigin.OPERATOR);
        assertThrows(ValidationException.class, () -> service.validateNew(active));
    }

    @Test
    void activatingDecidesWhetherTheSegmentIsClosed() {
        RoadIncident incident = pending();
        service.activate(incident, true);
        assertTrue(service.affectsRouting(incident));
        assertTrue(service.blocksTraffic(incident));

        RoadIncident slow = pending();
        service.activate(slow, false);
        assertTrue(service.affectsRouting(slow));
        assertFalse(service.blocksTraffic(slow));
    }

    @Test
    void rejectingOrClosingEndsTheIncident() {
        RoadIncident rejected = pending();
        service.reject(rejected, START.plusMinutes(5));
        assertEquals(IncidentStatus.REJECTED, rejected.getStatus());
        assertEquals(START.plusMinutes(5), rejected.getEndedAt());

        RoadIncident closed = pending();
        assertThrows(BusinessRuleException.class, () -> service.close(closed, START.plusMinutes(5)));
        service.activate(closed, true);
        service.close(closed, START.plusHours(2));
        assertFalse(service.affectsRouting(closed));
    }

    @Test
    void theEndCannotBeBeforeTheStart() {
        RoadIncident incident = pending();
        assertThrows(ValidationException.class, () -> service.reject(incident, START.minusMinutes(1)));
        assertEquals(IncidentStatus.PENDING, incident.getStatus());
    }
}
