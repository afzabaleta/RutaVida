
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.PanicReportView;
import co.edu.unicesar.rutavida.dto.PanicResult;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.PanicTestSupport;
import co.edu.unicesar.rutavida.util.Messages;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.POLICE;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PanicController with the real service on in-memory DAOs, without Oracle. */
class PanicControllerTest {

    private PanicTestSupport support;
    private SessionContext session;
    private PanicController controller;

    @BeforeEach
    void setUp() {
        support = new PanicTestSupport();

        AuthService authService = new AuthService(
                support.users,
                support.roles,
                new PasswordHasher(1_000),
                new AuditService(new InMemoryAuditDAO(), CLOCK),
                CLOCK
        );

        session = new SessionContext();
        controller = new PanicController(
                support.service(),
                authService,
                session
        );
    }

    @Test
    void aCitizenReportsWithItsAccountAndIsToldToWait() {
        assertEquals(ErrorKind.AUTHENTICATION, controller.report(press("celular-1", 0)).error(),
                "DT-13: without an account the button does not report");

        session.start(support.citizen(1));
        OperationResult<PanicResult> result =
                controller.report(press("celular-1", 0));

        assertTrue(result.success());
        assertEquals(
                PanicController.WAITING_FOR_OTHERS,
                result.message()
        );
        assertEquals(
                PanicReportStatus.PENDING,
                result.value().status()
        );
    }

    @Test
    void aPoliceOfficerWithSessionValidatesAndIsToldHelpIsOnItsWay() {
        session.start(support.sessionOf(POLICE));

        OperationResult<PanicResult> result =
                controller.report(press("celular-policia", 0));

        assertTrue(result.success());
        assertEquals(
                PanicController.HELP_ON_ITS_WAY,
                result.message()
        );
        assertEquals(
                PanicReportStatus.VALIDATED,
                result.value().status()
        );
    }

    @Test
    void listingAndDiscardingNeedSessionAndPermission() {

        session.start(support.citizen(1));
        long id = controller.report(
                press("celular-1", 0)
        ).value().reportId();
        session.end();

        assertEquals(
                ErrorKind.AUTHENTICATION,
                controller.listRecent(null).error()
        );

        assertEquals(
                Messages.SESSION_REQUIRED,
                controller.discard(id).message()
        );

        session.start(support.sessionOf(POLICE));

        OperationResult<List<PanicReportView>> list =
                controller.listRecent(null);

        assertTrue(list.success());
        assertEquals(1, list.value().size());

        assertEquals(
                ErrorKind.PERMISSION,
                controller.discard(id).error(),
                "the police only views"
        );

        session.start(support.sessionOf(COORDINATOR));

        OperationResult<PanicReportView> discarded =
                controller.discard(id);

        assertTrue(discarded.success());

        assertEquals(
                PanicReportStatus.DISCARDED,
                discarded.value().status()
        );

        assertFalse(controller.discard(id).success());
    }
}
