
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.HospitalRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.ADMIN;
import static co.edu.unicesar.rutavida.service.AdministrationTestSupport.VALLEDUPAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HospitalService (CU-05) on in-memory DAOs, without Oracle.
 * Hospital 1 is HOSPITAL EJEMPLO UNO.
 */
class HospitalServiceTest {

    private final AdministrationTestSupport support =
            new AdministrationTestSupport();

    private final HospitalService service = support.hospitalService();

    private static HospitalRequest request(
            String name,
            String phone,
            GeoPoint location,
            Integer capacity) {

        return new HospitalRequest(
                name,
                HospitalLevel.LEVEL_III,
                " Calle de EJEMPLO 1 ",
                phone,
                location,
                capacity
        );
    }

    @Test
    void registersAnActiveHospitalAndAudits() {
        Hospital hospital = service.register(
                request(" HOSPITAL EJEMPLO DOS ",
                        "605 5700000", VALLEDUPAR, 20),
                support.sessionOf(ADMIN)
        );

        assertTrue(hospital.getId() > 1);
        assertEquals("HOSPITAL EJEMPLO DOS", hospital.getName());
        assertEquals("Calle de EJEMPLO 1", hospital.getAddress());
        assertEquals(RecordStatus.ACTIVE, hospital.getStatus());
        assertEquals(2, service.listOperational().size());
        assertEquals("CREAR", support.auditDao.last().getOperation());
        assertEquals(hospital.getId(), support.auditDao.last().getRecordId());
        assertEquals(1, support.transactions.calls);
    }

    @Test
    void wrongDataAndRepeatedNamesAreRejected() {
        var admin = support.sessionOf(ADMIN);

        assertThrows(ValidationException.class,
                () -> service.register(
                        request(" ", null, VALLEDUPAR, null), admin));

        assertThrows(ValidationException.class,
                () -> service.register(
                        request("H", "teléfono", VALLEDUPAR, null), admin));

        assertThrows(ValidationException.class,
                () -> service.register(
                        request("H", null, null, null), admin));

        assertThrows(ValidationException.class,
                () -> service.register(
                        request("H", null, new GeoPoint(95, 0), null), admin));

        assertThrows(ValidationException.class,
                () -> service.register(
                        request("H", null, VALLEDUPAR, -1), admin));

        assertThrows(ValidationException.class,
                () -> service.register(
                        new HospitalRequest(
                                "H", null, "Calle", null, VALLEDUPAR, null
                        ), admin));

        assertEquals(
                "Ya existe un hospital con ese nombre.",
                assertThrows(
                        BusinessRuleException.class,
                        () -> service.register(
                                request("hospital ejemplo uno",
                                        null, VALLEDUPAR, null), admin
                        )
                ).getMessage()
        );

        assertEquals(1, support.hospitalDao.findAll().size());
        assertTrue(support.auditDao.saved.isEmpty());
    }

    @Test
    void anUpdateKeepsTheStatusAndItsOwnName() {
        var admin = support.sessionOf(ADMIN);

        Hospital updated = service.update(
                1,
                request("HOSPITAL EJEMPLO UNO", null, VALLEDUPAR, null),
                admin
        );

        assertEquals(HospitalLevel.LEVEL_III, updated.getLevel());
        assertNull(updated.getEmergencyCapacity());
        assertEquals(RecordStatus.ACTIVE, updated.getStatus());
        assertEquals("EDITAR", support.auditDao.last().getOperation());

        assertThrows(
                NotFoundException.class,
                () -> service.update(
                        99,
                        request("X", null, VALLEDUPAR, null),
                        admin
                )
        );
    }

    @Test
    void anInactiveHospitalIsNotADestination() {
        var admin = support.sessionOf(ADMIN);

        service.changeStatus(1, RecordStatus.INACTIVE, admin);

        assertTrue(service.listOperational().isEmpty());
        assertEquals("DESACTIVAR", support.auditDao.last().getOperation());

        assertThrows(
                BusinessRuleException.class,
                () -> service.changeStatus(
                        1, RecordStatus.INACTIVE, admin
                )
        );

        service.changeStatus(1, RecordStatus.ACTIVE, admin);

        assertEquals(1, service.listOperational().size());
        assertEquals("ACTIVAR", support.auditDao.last().getOperation());
    }
}
