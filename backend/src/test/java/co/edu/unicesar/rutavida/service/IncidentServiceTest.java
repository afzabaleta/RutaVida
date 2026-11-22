
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rules of the incidents and the use cases CU-13 and CU-14 with in-memory DAOs, without
 * Oracle. Test data only: invented incidents.
 */
class IncidentServiceTest {

    private static final LocalDateTime START =
            LocalDateTime.of(2026, 10, 28, 8, 0);

    private static final LocalDateTime NOW =
            LocalDateTime.now(EmergencyTestSupport.CLOCK);

    private static final UserSession OPERATOR =
            EmergencyTestSupport.session("INCIDENTES:GESTIONAR");

    private final IncidentTestSupport support = new IncidentTestSupport();
    private final IncidentService service = support.service();

    private static RoadIncident pending() {
        return new RoadIncident(
                0, 7, IncidentType.ACCIDENT, Severity.MEDIUM,
                false, "Choque simple", START, null,
                IncidentStatus.PENDING, IncidentOrigin.OPERATOR
        );
    }

    @Test
    void aNewIncidentStartsPendingAndDoesNotAffectRoutes() {
        RoadIncident incident = pending();
        service.validateNew(incident);

        assertFalse(service.affectsRouting(incident));

        RoadIncident active = new RoadIncident(
                0, 7, IncidentType.ACCIDENT, Severity.MEDIUM,
                false, null, START, null,
                IncidentStatus.ACTIVE, IncidentOrigin.OPERATOR
        );

        assertThrows(ValidationException.class,
                () -> service.validateNew(active));
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

        assertThrows(BusinessRuleException.class,
                () -> service.close(closed, START.plusMinutes(5)));

        service.activate(closed, true);
        service.close(closed, START.plusHours(2));

        assertFalse(service.affectsRouting(closed));
    }

    @Test
    void theEndCannotBeBeforeTheStart() {
        RoadIncident incident = pending();

        assertThrows(ValidationException.class,
                () -> service.reject(incident, START.minusMinutes(1)));

        assertEquals(IncidentStatus.PENDING, incident.getStatus());
    }

    // CU-13: report

    @Test
    void aReportIsSavedPendingWithTheServerTimeAndAudited() {
        RoadIncident incident = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR,
                OPERATOR
        );

        assertEquals(1, incident.getId());
        assertEquals(IncidentStatus.PENDING, incident.getStatus());
        assertEquals(NOW, incident.getStartedAt());
        assertNull(incident.getEndedAt());

        assertTrue(incident.isBlocksSegment());
        assertFalse(service.affectsRouting(incident));

        assertEquals(
                IncidentStatus.PENDING,
                support.incidentDao.stored(1).getStatus()
        );

        assertEquals(1, support.transactions.calls);

        AuditLog audit = support.auditDao.last();

        assertEquals("INCIDENTES", audit.getModule());
        assertEquals("REGISTRAR", audit.getOperation());
        assertEquals(1L, audit.getRecordId());
        assertEquals(
                EmergencyTestSupport.OPERATOR_ID,
                audit.getUserId()
        );
    }

    @Test
    void aReportWithWrongDataSavesNothing() {
        ValidationException missing = assertThrows(
                ValidationException.class,
                () -> service.report(
                        new IncidentRequest(
                                999, IncidentType.ACCIDENT,
                                Severity.HIGH, false, null
                        ),
                        IncidentOrigin.OPERATOR,
                        OPERATOR
                )
        );

        assertEquals("El tramo vial no existe.", missing.getMessage());

        assertThrows(ValidationException.class,
                () -> service.report(
                        new IncidentRequest(
                                0, IncidentType.ACCIDENT,
                                Severity.HIGH, false, null
                        ),
                        IncidentOrigin.OPERATOR, OPERATOR
                ));

        assertThrows(ValidationException.class,
                () -> service.report(
                        new IncidentRequest(
                                IncidentTestSupport.SEGMENT_ID,
                                null, Severity.HIGH, false, null
                        ),
                        IncidentOrigin.OPERATOR, OPERATOR
                ));

        assertThrows(ValidationException.class,
                () -> service.report(
                        new IncidentRequest(
                                IncidentTestSupport.SEGMENT_ID,
                                IncidentType.ROADWORK,
                                null, false, null
                        ),
                        IncidentOrigin.OPERATOR, OPERATOR
                ));

        assertThrows(ValidationException.class,
                () -> service.report(
                        new IncidentRequest(
                                IncidentTestSupport.SEGMENT_ID,
                                IncidentType.OTHER,
                                Severity.LOW, false,
                                "x".repeat(301)
                        ),
                        IncidentOrigin.OPERATOR, OPERATOR
                ));

        assertThrows(ValidationException.class,
                () -> service.report(
                        null, IncidentOrigin.OPERATOR, OPERATOR
                ));

        assertTrue(service.listOpen().isEmpty());
        assertTrue(support.auditDao.saved.isEmpty());
    }

    // CU-14: change status

    @Test
    void activatingSavesItAndUsesTheReportedClosure() {
        long id = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR,
                OPERATOR
        ).getId();

        RoadIncident active =
                service.changeStatus(id, IncidentStatus.ACTIVE);

        assertEquals(IncidentStatus.ACTIVE, active.getStatus());

        RoadIncident stored = support.incidentDao.stored(id);

        assertEquals(IncidentStatus.ACTIVE, stored.getStatus());
        assertTrue(service.blocksTraffic(stored));
        assertEquals(1, support.incidentDao.updates);

        assertEquals(1, support.auditDao.saved.size());
    }

    @Test
    void rejectingOrClosingSavesTheEndTime() {
        long rejected = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR,
                OPERATOR
        ).getId();

        long closed = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR,
                OPERATOR
        ).getId();

        service.changeStatus(rejected, IncidentStatus.REJECTED);

        service.changeStatus(closed, IncidentStatus.ACTIVE);
        service.changeStatus(closed, IncidentStatus.CLOSED);

        assertEquals(
                IncidentStatus.REJECTED,
                support.incidentDao.stored(rejected).getStatus()
        );

        assertEquals(
                NOW,
                support.incidentDao.stored(rejected).getEndedAt()
        );

        assertEquals(
                IncidentStatus.CLOSED,
                support.incidentDao.stored(closed).getStatus()
        );

        assertEquals(
                NOW,
                support.incidentDao.stored(closed).getEndedAt()
        );

        assertFalse(
                service.affectsRouting(support.incidentDao.stored(closed))
        );
    }

    @Test
    void forbiddenChangesFailAndSaveNothing() {
        long id = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR,
                OPERATOR
        ).getId();

        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(id, IncidentStatus.CLOSED));

        assertThrows(ValidationException.class,
                () -> service.changeStatus(id, IncidentStatus.PENDING));

        assertThrows(ValidationException.class,
                () -> service.changeStatus(id, null));

        assertThrows(NotFoundException.class,
                () -> service.changeStatus(99, IncidentStatus.ACTIVE));

        assertEquals(
                IncidentStatus.PENDING,
                support.incidentDao.stored(id).getStatus()
        );

        assertEquals(0, support.incidentDao.updates);

        service.changeStatus(id, IncidentStatus.ACTIVE);

        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(id, IncidentStatus.REJECTED));
    }

    // Queries

    @Test
    void openIncidentsArePendingAndActive() {
        long first = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR, OPERATOR
        ).getId();

        long second = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR, OPERATOR
        ).getId();

        long third = service.report(
                IncidentTestSupport.CLOSING_ACCIDENT,
                IncidentOrigin.OPERATOR, OPERATOR
        ).getId();

        service.changeStatus(second, IncidentStatus.ACTIVE);
        service.changeStatus(third, IncidentStatus.REJECTED);

        assertEquals(
                List.of(first, second),
                service.listOpen().stream()
                        .map(RoadIncident::getId).toList()
        );

        assertEquals(
                List.of(second),
                service.listByStatus(IncidentStatus.ACTIVE)
                        .stream().map(RoadIncident::getId).toList()
        );

        assertEquals(
                List.of(third),
                service.listByStatus(IncidentStatus.REJECTED)
                        .stream().map(RoadIncident::getId).toList()
        );

        assertEquals(second, service.findById(second).getId());

        assertThrows(NotFoundException.class,
                () -> service.findById(99));
    }
}
