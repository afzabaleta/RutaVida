
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.service.AlertTestSupport;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.AlertTestSupport.ADMINISTRATOR;
import static co.edu.unicesar.rutavida.service.AlertTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.AlertTestSupport.MISSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AlertController with the real service on in-memory DAOs, without Oracle. */
class AlertControllerTest {

    private AlertTestSupport support;
    private AlertService service;
    private SessionContext session;
    private AlertController controller;

    @BeforeEach
    void setUp() {
        support = new AlertTestSupport();
        service = support.service();
        session = new SessionContext();
        controller = new AlertController(service, session);
    }

    @Test
    void theUserOfTheSessionReadsAndMarksHisAlerts() {
        long id = service.routeRecalculated(
                MISSION, "EJEMPLO-AMB-01", 1.5
        ).getId();

        session.start(support.sessionOf(COORDINATOR));

        OperationResult<List<Alert>> unread = controller.list(true);

        assertTrue(unread.success());
        assertEquals(
                List.of(id),
                unread.value().stream().map(Alert::getId).toList()
        );

        OperationResult<Alert> read = controller.markAsRead(id);

        assertTrue(read.success());
        assertTrue(read.value().isRead());
        assertTrue(controller.list(true).value().isEmpty());

        assertEquals(1, controller.list(false).value().size());
    }

    @Test
    void errorsCarryTheirKindForTheWebApi() {
        assertEquals(
                ErrorKind.AUTHENTICATION,
                controller.list(false).error(),
                "no session"
        );

        assertEquals(
                Messages.SESSION_REQUIRED,
                controller.list(false).message()
        );

        long id = service.routeRecalculated(
                MISSION, "EJEMPLO-AMB-01", 1.5
        ).getId();

        session.start(support.sessionOf(ADMINISTRATOR));

        OperationResult<Alert> other = controller.markAsRead(id);

        assertEquals(
                ErrorKind.NOT_FOUND,
                other.error(),
                "404 in the API: it is not his alert"
        );

        assertEquals(
                Messages.NOT_FOUND,
                other.message()
        );
    }
}
