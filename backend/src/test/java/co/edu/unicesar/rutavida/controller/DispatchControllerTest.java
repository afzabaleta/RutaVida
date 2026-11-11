package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static co.edu.unicesar.rutavida.service.DispatchTestSupport.ACCIDENT;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.BUSY_AMBULANCE;
import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DispatchController with the real service on the small city of DispatchTestSupport, without Oracle. */
class DispatchControllerTest {

    private DispatchTestSupport city;
    private SessionContext session;
    private DispatchController controller;

    @BeforeEach
    void setUp() {
        city = new DispatchTestSupport();
        AuthService authService = new AuthService(new InMemoryUserDAO(), new InMemoryRoleDAO(),
                new PasswordHasher(1_000), new AuditService(new InMemoryAuditDAO(), CLOCK), CLOCK);
        session = new SessionContext();
        controller = new DispatchController(city.service(), authService, session);
    }

    @Test
    void aCoordinatorSeesTheUnitsTheRoutesAndDispatches() {
        session.start(EmergencyTestSupport.session("DESPACHO:GESTIONAR", "RUTAS:GESTIONAR", "RUTAS:CONSULTAR"));
        long accident = city.report(ACCIDENT, 1, 2);

        assertEquals(AMBULANCE, controller.candidates(accident).value().get(0).vehicleId());
        assertFalse(controller.preview(accident, AMBULANCE).value().isEmpty());

        OperationResult<DispatchResult> sent = controller.dispatch(accident, AMBULANCE);
        assertTrue(sent.success());
        long mission = sent.value().assignment().getId();

        assertFalse(controller.missionRoutes(mission).value().isEmpty());
        assertEquals(AssignmentStatus.EN_ROUTE,
                controller.advance(mission, AssignmentStatus.EN_ROUTE).value().getStatus());
    }

    @Test
    void errorsCarryTheirKindForTheWebApi() {
        long accident = city.report(ACCIDENT, 1, 2);
        assertEquals(ErrorKind.AUTHENTICATION, controller.candidates(accident).error(), "no session");

        session.start(EmergencyTestSupport.session("DESPACHO:GESTIONAR"));
        assertEquals(ErrorKind.PERMISSION, controller.preview(accident, AMBULANCE).error(),
                "needs RUTAS / GESTIONAR");
        assertEquals(ErrorKind.PERMISSION, controller.missionRoutes(1).error(), "needs RUTAS / CONSULTAR");

        assertEquals(ErrorKind.BUSINESS_RULE, controller.dispatch(accident, BUSY_AMBULANCE).error(),
                "409 in the API");
        assertEquals(ErrorKind.NOT_FOUND, controller.dispatch(99, AMBULANCE).error());
        assertEquals(ErrorKind.NOT_FOUND, controller.advance(99, AssignmentStatus.EN_ROUTE).error());
        assertEquals(ErrorKind.VALIDATION, controller.advance(1, null).error());
    }
}