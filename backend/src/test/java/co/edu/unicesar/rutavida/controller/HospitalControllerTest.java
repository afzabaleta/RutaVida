
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.HospitalRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.service.AdministrationTestSupport;
import org.junit.jupiter.api.Test;

import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.ADMIN;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.OPERATOR;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.VALLEDUPAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HospitalController with the real service on in-memory DAOs,
 * without Oracle.
 */
class HospitalControllerTest {

    private static final HospitalRequest NEW_HOSPITAL =
            new HospitalRequest(
                    "HOSPITAL EJEMPLO DOS",
                    HospitalLevel.LEVEL_II,
                    "Calle de EJEMPLO 2",
                    null,
                    VALLEDUPAR,
                    8
            );

    private final AdministrationTestSupport support =
            new AdministrationTestSupport();

    private final SessionContext session = new SessionContext();

    private final HospitalController controller =
            new HospitalController(
                    support.hospitalService(),
                    support.authService(),
                    session
            );

    @Test
    void anAdministratorRegistersAndDeactivatesAHospital() {
        session.start(support.sessionOf(ADMIN));

        OperationResult<Hospital> registered =
                controller.register(NEW_HOSPITAL);

        OperationResult<Hospital> inactive =
                controller.changeStatus(
                        registered.value().getId(),
                        RecordStatus.INACTIVE
                );

        assertTrue(registered.success(), registered.message());

        assertEquals(
                RecordStatus.INACTIVE,
                inactive.value().getStatus()
        );

        assertEquals(
                1,
                controller.listOperational().value().size()
        );
    }

    @Test
    void anOperatorSeesButDoesNotManage() {
        session.start(support.sessionOf(OPERATOR));

        assertTrue(controller.listOperational().success());

        assertEquals(
                ErrorKind.PERMISSION,
                controller.register(NEW_HOSPITAL).error()
        );

        assertEquals(
                ErrorKind.PERMISSION,
                controller.changeStatus(
                        1,
                        RecordStatus.INACTIVE
                ).error()
        );

        assertEquals(
                ErrorKind.NOT_FOUND,
                sessionAdmin().update(99, NEW_HOSPITAL).error()
        );
    }

    private HospitalController sessionAdmin() {
        session.end();
        session.start(support.sessionOf(ADMIN));
        return controller;
    }
}
