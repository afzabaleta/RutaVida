package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.service.CatalogService;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogControllerTest {

    private static final List<EmergencyType> TYPES = List.of(
            new EmergencyType(1, "EMERGENCIA_MEDICA", 9, "Urgencia medica sin accidente de transito"),
            new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Accidente de transito con posibles lesionados"));

    /** Test double: returns the list or fails like Oracle down. */
    private static EmergencyTypeDAO types(boolean oracleDown) {
        return new EmergencyTypeDAO() {
            @Override
            public Optional<EmergencyType> findById(long id) {
                return TYPES.stream().filter(type -> type.getId() == id).findFirst();
            }

            @Override
            public List<EmergencyType> findAll() {
                if (oracleDown) {
                    throw new DataAccessException("ORA-12541: TNS:no listener", null);
                }
                return TYPES;
            }
        };
    }

    @Test
    void theEmergencyTypesComeFromTheService() {
        CatalogController controller = new CatalogController(new CatalogService(types(false)));

        OperationResult<List<EmergencyType>> result = controller.emergencyTypes();

        assertTrue(result.success());
        assertEquals(TYPES, result.value());
    }

    @Test
    void oracleDownIsAnUnavailableErrorWithoutTechnicalDetails() {
        CatalogController controller = new CatalogController(new CatalogService(types(true)));

        OperationResult<List<EmergencyType>> result = controller.emergencyTypes();

        assertFalse(result.success());
        assertEquals(ErrorKind.UNAVAILABLE, result.error());
        assertEquals(Messages.DATABASE_DOWN, result.message());
    }
}