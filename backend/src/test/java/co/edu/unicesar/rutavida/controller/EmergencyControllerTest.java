
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.EmergencyHistory;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.service.*;
import co.edu.unicesar.rutavida.util.Messages;
import co.edu.unicesar.rutavida.util.PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.ACCIDENT;
import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmergencyController with the real services on in-memory DAOs,
 * without Oracle.
 */
class EmergencyControllerTest {

    private static final GeoPoint POPA =
            new GeoPoint(10.467172, -73.265099);

    private static final EmergencyRequest REQUEST =
            new EmergencyRequest(
                    ACCIDENT.getId(),
                    "Frente al Parque La Popa",
                    POPA,
                    Severity.HIGH,
                    null
            );

    private InMemoryEmergencyDAO emergencyDao;
    private SessionContext session;
    private EmergencyController controller;

    @BeforeEach
    void setUp() {

        emergencyDao = new InMemoryEmergencyDAO();

        InMemoryEmergencyTypeDAO typeDao =
                new InMemoryEmergencyTypeDAO();

        typeDao.add(ACCIDENT);

        AuditService auditService = new AuditService(
                new InMemoryAuditDAO(),
                CLOCK
        );

        EmergencyService emergencyService =
                new EmergencyService(
                        emergencyDao,
                        typeDao,
                        new EmergencyTestSupport.FixedAssignments(),
                        new EmergencyTestSupport.FixedRoutes(),
                        auditService,
                        new EmergencyTestSupport.CountingTransactions(),
                        CLOCK
                );

        AuthService authService = new AuthService(
                new InMemoryUserDAO(),
                new InMemoryRoleDAO(),
                new PasswordHasher(1_000),
                auditService,
                CLOCK
        );

        session = new SessionContext();

        controller = new EmergencyController(
                emergencyService,
                authService,
                session
        );
    }

    @Test
    void anOperatorRegistersAndSeesTheEmergency() {

        session.start(EmergencyTestSupport.session(
                "EMERGENCIAS:GESTIONAR",
                "EMERGENCIAS:CONSULTAR"
        ));

        OperationResult<Emergency> registered =
                controller.register(REQUEST);

        assertTrue(registered.success());
        assertNull(registered.message());

        OperationResult<List<Emergency>> active =
                controller.listActive();

        assertTrue(active.success());

        assertEquals(
                List.of(registered.value()),
                active.value()
        );

        OperationResult<EmergencyHistory> history =
                controller.history(registered.value().getId());

        assertTrue(history.success());

        assertEquals(
                registered.value(),
                history.value().emergency()
        );
    }

    @Test
    void withoutSessionOrPermissionNothingIsDone() {

        OperationResult<Emergency> noSession =
                controller.register(REQUEST);

        assertFalse(noSession.success());

        assertEquals(
                Messages.SESSION_REQUIRED,
                noSession.message()
        );

        session.start(EmergencyTestSupport.session(
                "EMERGENCIAS:CONSULTAR"
        ));

        OperationResult<Emergency> noPermission =
                controller.register(REQUEST);

        assertFalse(noPermission.success());

        assertEquals(
                "No tiene permiso para realizar esta operación.",
                noPermission.message()
        );

        assertTrue(emergencyDao.saved.isEmpty());
    }

    @Test
    void errorsComeBackAsMessagesInSpanish() {

        session.start(EmergencyTestSupport.session(
                "EMERGENCIAS:GESTIONAR",
                "EMERGENCIAS:CONSULTAR"
        ));

        OperationResult<Emergency> invalid =
                controller.register(
                        new EmergencyRequest(
                                ACCIDENT.getId(),
                                " ",
                                POPA,
                                Severity.HIGH,
                                null
                        )
                );

        assertFalse(invalid.success());

        assertEquals(
                "El campo dirección de referencia es obligatorio.",
                invalid.message()
        );

        OperationResult<EmergencyHistory> missing =
                controller.history(999);

        assertFalse(missing.success());

        assertEquals(
                Messages.NOT_FOUND,
                missing.message()
        );
    }

    @Test
    void oneEmergencyIsFoundById() {
        session.start(EmergencyTestSupport.session("EMERGENCIAS:GESTIONAR", "EMERGENCIAS:CONSULTAR"));
        Emergency registered = controller.register(REQUEST).value();

        OperationResult<Emergency> found = controller.find(registered.getId());

        assertTrue(found.success());
        assertEquals(registered, found.value());
    }

    @Test
    void errorsCarryTheirKindForTheWebApi() {
        assertEquals(ErrorKind.AUTHENTICATION, controller.register(REQUEST).error(), "no session");

        session.start(EmergencyTestSupport.session("EMERGENCIAS:CONSULTAR"));
        assertEquals(ErrorKind.PERMISSION, controller.register(REQUEST).error(), "no permission");

        session.start(EmergencyTestSupport.session("EMERGENCIAS:GESTIONAR", "EMERGENCIAS:CONSULTAR"));
        assertEquals(ErrorKind.VALIDATION, controller.register(
                new EmergencyRequest(ACCIDENT.getId(), " ", POPA, Severity.HIGH, null)).error(), "no address");
        assertEquals(ErrorKind.NOT_FOUND, controller.find(999).error());
        assertEquals(ErrorKind.NOT_FOUND, controller.history(999).error());
    }
}