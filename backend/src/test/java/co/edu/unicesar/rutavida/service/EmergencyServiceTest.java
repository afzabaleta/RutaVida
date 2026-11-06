
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.EmergencyHistory;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.ACCIDENT;
import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.OPERATOR_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test data only: an invented emergency at Parque La Popa. */
class EmergencyServiceTest {

    private static final LocalDateTime REPORTED =
            LocalDateTime.of(2026, 10, 28, 10, 0);

    private static final GeoPoint POPA =
            new GeoPoint(10.467172, -73.265099);

    private InMemoryEmergencyDAO emergencyDao;
    private InMemoryAuditDAO auditDao;
    private EmergencyTestSupport.CountingTransactions transactions;
    private EmergencyTestSupport.FixedAssignments assignments;
    private EmergencyTestSupport.FixedRoutes routes;
    private EmergencyService service;
    private UserSession session;

    @BeforeEach
    void setUp() {
        emergencyDao = new InMemoryEmergencyDAO();

        InMemoryEmergencyTypeDAO typeDao =
                new InMemoryEmergencyTypeDAO();
        typeDao.add(ACCIDENT);

        auditDao = new InMemoryAuditDAO();
        transactions = new EmergencyTestSupport.CountingTransactions();
        assignments = new EmergencyTestSupport.FixedAssignments();
        routes = new EmergencyTestSupport.FixedRoutes();

        service = new EmergencyService(
                emergencyDao,
                typeDao,
                assignments,
                routes,
                new AuditService(auditDao, CLOCK),
                transactions,
                CLOCK
        );

        session = EmergencyTestSupport.session(
                "EMERGENCIAS:GESTIONAR"
        );
    }

    private static Emergency emergency(EmergencyStatus status) {
        return new Emergency(
                0,
                1,
                1,
                "Parque La Popa",
                POPA,
                REPORTED,
                Severity.HIGH,
                "Prueba",
                status,
                null
        );
    }

    @Test
    void aValidNewEmergencyPasses() {
        service.validateNew(emergency(EmergencyStatus.REPORTED));
        assertTrue(service.isActive(emergency(EmergencyStatus.REPORTED)));
    }

    @Test
    void aNewEmergencyMustStartReportedAndHaveItsData() {
        assertThrows(ValidationException.class,
                () -> service.validateNew(
                        emergency(EmergencyStatus.ASSIGNED)));

        Emergency noAddress = new Emergency(
                0, 1, 1, " ",
                new GeoPoint(10.46, -73.26),
                REPORTED, Severity.HIGH, null,
                EmergencyStatus.REPORTED, null
        );

        assertThrows(ValidationException.class,
                () -> service.validateNew(noAddress));

        Emergency badPoint = new Emergency(
                0, 1, 1, "Calle 1",
                new GeoPoint(95, -73.26),
                REPORTED, Severity.HIGH, null,
                EmergencyStatus.REPORTED, null
        );

        assertThrows(ValidationException.class,
                () -> service.validateNew(badPoint));

        Emergency noType = new Emergency(
                0, 0, 1, "Calle 1",
                new GeoPoint(10.46, -73.26),
                REPORTED, Severity.HIGH, null,
                EmergencyStatus.REPORTED, null
        );

        assertThrows(ValidationException.class,
                () -> service.validateNew(noType));
    }

    @Test
    void theNormalFlowIsAllowedAndClosingRecordsTheTime() {
        Emergency e = emergency(EmergencyStatus.REPORTED);

        service.changeStatus(e, EmergencyStatus.ASSIGNED,
                REPORTED.plusMinutes(1));
        service.changeStatus(e, EmergencyStatus.EN_ROUTE,
                REPORTED.plusMinutes(2));
        service.changeStatus(e, EmergencyStatus.ON_SCENE,
                REPORTED.plusMinutes(8));

        assertNull(e.getClosedAt());

        service.changeStatus(e, EmergencyStatus.CLOSED,
                REPORTED.plusMinutes(30));

        assertEquals(EmergencyStatus.CLOSED, e.getStatus());
        assertEquals(REPORTED.plusMinutes(30), e.getClosedAt());
        assertFalse(service.isActive(e));
    }

    @Test
    void stepsCannotBeSkippedAndAClosedEmergencyIsFinal() {
        Emergency e = emergency(EmergencyStatus.REPORTED);

        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(
                        e,
                        EmergencyStatus.ON_SCENE,
                        REPORTED.plusMinutes(1)));

        service.changeStatus(e, EmergencyStatus.CLOSED,
                REPORTED.plusMinutes(1));

        for (EmergencyStatus next : EmergencyStatus.values()) {
            assertFalse(service.canChange(
                    EmergencyStatus.CLOSED, next));
        }
    }

    @Test
    void itCannotCloseBeforeTheReport() {
        Emergency e = emergency(EmergencyStatus.REPORTED);

        assertThrows(ValidationException.class,
                () -> service.changeStatus(
                        e,
                        EmergencyStatus.CLOSED,
                        REPORTED.minusMinutes(1)));

        assertEquals(EmergencyStatus.REPORTED, e.getStatus());
    }

    // ------------------------------------------------------------------
    // CU-06, CU-07, CU-15
    // ------------------------------------------------------------------

    @Test
    void registerSavesAReportedEmergencyAndAuditsIt() {
        EmergencyRequest request = new EmergencyRequest(
                ACCIDENT.getId(),
                "   Frente al Parque La Popa",
                POPA,
                Severity.HIGH,
                "   "
        );

        Emergency saved = service.register(request, session);

        assertEquals(1, transactions.calls,
                "insert and audit in one transaction");

        assertTrue(saved.getId() > 0);
        assertSame(saved, emergencyDao.saved.get(saved.getId()));
        assertEquals(EmergencyStatus.REPORTED, saved.getStatus());
        assertEquals(OPERATOR_ID, saved.getReportedByUserId());
        assertEquals(LocalDateTime.now(CLOCK), saved.getReportedAt());

        assertEquals("Frente al Parque La Popa",
                saved.getAddressReference(),
                "the service trims the text");

        assertNull(saved.getDescription(),
                "a blank description is saved as null");

        assertNull(saved.getClosedAt());

        AuditLog log = auditDao.last();

        assertEquals("EMERGENCIAS", log.getModule());
        assertEquals("REGISTRAR", log.getOperation());
        assertEquals(Long.valueOf(saved.getId()), log.getRecordId());
        assertEquals(Long.valueOf(OPERATOR_ID), log.getUserId());
        assertEquals(AuditResult.SUCCESS, log.getResult());
    }

    @Test
    void registerRejectsWrongDataAndSavesNothing() {
        assertThrows(ValidationException.class,
                () -> service.register(
                        new EmergencyRequest(
                                99, "Calle 1", POPA,
                                Severity.HIGH, null),
                        session),
                "unknown type");

        assertThrows(ValidationException.class,
                () -> service.register(
                        new EmergencyRequest(
                                ACCIDENT.getId(), " ", POPA,
                                Severity.HIGH, null),
                        session),
                "no address");

        assertThrows(ValidationException.class,
                () -> service.register(
                        new EmergencyRequest(
                                ACCIDENT.getId(), "Calle 1", null,
                                Severity.HIGH, null),
                        session),
                "no point");

        assertThrows(ValidationException.class,
                () -> service.register(
                        new EmergencyRequest(
                                ACCIDENT.getId(), "Calle 1", POPA,
                                null, null),
                        session),
                "no severity");

        assertThrows(ValidationException.class,
                () -> service.register(
                        new EmergencyRequest(
                                ACCIDENT.getId(), "Calle 1", POPA,
                                Severity.HIGH, null),
                        null),
                "no session");

        assertTrue(emergencyDao.saved.isEmpty());
        assertTrue(auditDao.saved.isEmpty());
        assertEquals(0, transactions.calls);
    }

    @Test
    void listActiveLeavesOutClosedEmergencies() {
        Emergency open = service.register(
                new EmergencyRequest(
                        ACCIDENT.getId(), "Calle 1", POPA,
                        Severity.LOW, null),
                session
        );

        Emergency closed = service.register(
                new EmergencyRequest(
                        ACCIDENT.getId(), "Calle 2", POPA,
                        Severity.LOW, null),
                session
        );

        emergencyDao.updateStatus(
                closed.getId(),
                EmergencyStatus.CLOSED,
                LocalDateTime.now(CLOCK).plusHours(1)
        );

        List<Emergency> active = service.listActive();

        assertEquals(List.of(open), active);
    }

    @Test
    void historyGathersTheEmergencyItsAssignmentsRoutesAndAudit() {
        Emergency saved = service.register(
                new EmergencyRequest(
                        ACCIDENT.getId(), "Calle 1", POPA,
                        Severity.CRITICAL, null),
                session
        );

        EmergencyHistory history = service.getHistory(saved.getId());

        assertSame(saved, history.emergency());
        assertTrue(history.assignments().isEmpty());
        assertTrue(history.routes().isEmpty());
        assertEquals(1, history.auditTrail().size());

        assertEquals("REGISTRAR",
                history.auditTrail().get(0).getOperation());

        assertThrows(NotFoundException.class,
                () -> service.getHistory(12345));
    }
}
