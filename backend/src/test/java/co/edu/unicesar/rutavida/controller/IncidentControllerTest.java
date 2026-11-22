
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.service.IncidentTestSupport;
import co.edu.unicesar.rutavida.util.Messages;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IncidentController with the real service on in-memory DAOs, without Oracle.
 */
class IncidentControllerTest {

    private IncidentTestSupport support;
    private SessionContext session;
    private IncidentController controller;

    @BeforeEach
    void setUp() {
        support = new IncidentTestSupport();

        AuthService authService = new AuthService(
                new InMemoryUserDAO(),
                new InMemoryRoleDAO(),
                new PasswordHasher(1_000),
                new AuditService(new InMemoryAuditDAO(), CLOCK),
                CLOCK
        );

        session = new SessionContext();

        controller = new IncidentController(
                support.service(),
                authService,
                session
        );
    }

    @Test
    void anOperatorReportsActivatesAndClosesAnIncident() {

        session.start(EmergencyTestSupport.session(
                "INCIDENTES:GESTIONAR",
                "INCIDENTES:CONSULTAR"
        ));

        OperationResult<RoadIncident> reported =
                controller.report(IncidentTestSupport.CLOSING_ACCIDENT);

        assertTrue(reported.success());

        RoadIncident incident = reported.value();

        assertEquals(
                IncidentOrigin.OPERATOR,
                incident.getOrigin(),
                "reported from the operator screen"
        );

        assertEquals(IncidentStatus.PENDING, incident.getStatus());

        assertEquals(
                List.of(incident.getId()),
                controller.listOpen().value().stream()
                        .map(RoadIncident::getId)
                        .toList()
        );

        OperationResult<RoadIncident> active =
                controller.changeStatus(
                        incident.getId(),
                        IncidentStatus.ACTIVE
                );

        assertTrue(active.success());
        assertEquals(IncidentStatus.ACTIVE, active.value().getStatus());

        assertEquals(
                1,
                controller.listByStatus(IncidentStatus.ACTIVE).value().size()
        );

        assertTrue(
                controller.changeStatus(
                        incident.getId(),
                        IncidentStatus.CLOSED
                ).success()
        );

        assertTrue(controller.listOpen().value().isEmpty());

        assertEquals(
                IncidentStatus.CLOSED,
                controller.find(incident.getId()).value().getStatus()
        );
    }

    @Test
    void errorsCarryTheirKindForTheWebApi() {

        assertEquals(
                ErrorKind.AUTHENTICATION,
                controller.listOpen().error(),
                "no session"
        );

        session.start(
                EmergencyTestSupport.session("INCIDENTES:CONSULTAR")
        );

        OperationResult<RoadIncident> denied =
                controller.report(IncidentTestSupport.CLOSING_ACCIDENT);

        assertEquals(ErrorKind.PERMISSION, denied.error());

        assertTrue(controller.listOpen().success());

        session.start(EmergencyTestSupport.session(
                "INCIDENTES:GESTIONAR",
                "INCIDENTES:CONSULTAR"
        ));

        OperationResult<RoadIncident> missingSegment =
                controller.report(
                        new IncidentRequest(
                                999,
                                IncidentType.ACCIDENT,
                                Severity.HIGH,
                                false,
                                null
                        )
                );

        assertFalse(missingSegment.success());
        assertEquals(ErrorKind.VALIDATION, missingSegment.error());
        assertEquals(
                "El tramo vial no existe.",
                missingSegment.message()
        );

        long id = controller.report(
                IncidentTestSupport.CLOSING_ACCIDENT
        ).value().getId();

        assertEquals(
                ErrorKind.BUSINESS_RULE,
                controller.changeStatus(id, IncidentStatus.CLOSED).error()
        );

        assertEquals(
                ErrorKind.VALIDATION,
                controller.changeStatus(id, IncidentStatus.PENDING).error()
        );

        OperationResult<RoadIncident> missing = controller.find(999);

        assertEquals(ErrorKind.NOT_FOUND, missing.error());
        assertEquals(Messages.NOT_FOUND, missing.message());

        assertEquals(
                ErrorKind.NOT_FOUND,
                controller.changeStatus(999, IncidentStatus.ACTIVE).error()
        );
    }
}
