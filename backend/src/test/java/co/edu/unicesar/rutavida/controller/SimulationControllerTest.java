package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.SimulationStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.simulation.SimulationEngine;
import co.edu.unicesar.rutavida.simulation.SimulationTestSupport;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SimulationController with the real engine on the small city of the tests, without Oracle. */
class SimulationControllerTest {

    private final SimulationTestSupport support = new SimulationTestSupport();
    private final SimulationEngine engine = support.engine();
    private final SessionContext session = new SessionContext();
    private final SimulationController controller = new SimulationController(engine,
            new AuthService(new InMemoryUserDAO(), new InMemoryRoleDAO(), new PasswordHasher(1_000),
                    new AuditService(new InMemoryAuditDAO(), CLOCK), CLOCK), session);

    @AfterEach
    void stopTheEngine() {
        engine.stop();
    }

    @Test
    void aCoordinatorStartsForcesTrafficAndStops() {
        session.start(EmergencyTestSupport.session("TRAFICO:CONSULTAR", "TRAFICO:GESTIONAR"));

        OperationResult<SimulationStatus> started = controller.start();
        assertTrue(started.success(), started.message());
        assertTrue(started.value().running());
        assertEquals(TrafficSource.SIMULATION, started.value().source());
        assertTrue(controller.status().value().running());

        long segment = support.city.graph.edge(0).dbId();
        assertTrue(controller.forceTraffic(segment, TrafficLevel.CONGESTED).success());
        assertEquals(ErrorKind.NOT_FOUND, controller.forceTraffic(99999, TrafficLevel.CONGESTED).error());
        assertEquals(ErrorKind.VALIDATION, controller.forceTraffic(segment, null).error());

        OperationResult<SimulationStatus> stopped = controller.stop();
        assertFalse(stopped.value().running());
        assertEquals(null, stopped.value().startedAt());
    }

    @Test
    void anOperatorOnlySeesTheStatus() {
        assertEquals(ErrorKind.AUTHENTICATION, controller.status().error(), "without a session");
        session.start(EmergencyTestSupport.session("TRAFICO:CONSULTAR"));

        assertTrue(controller.status().success());
        assertEquals(ErrorKind.PERMISSION, controller.start().error());
        assertEquals(ErrorKind.PERMISSION, controller.forceTraffic(1, TrafficLevel.CONGESTED).error());
        assertFalse(engine.isRunning());
    }
}
