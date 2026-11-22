package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.Hospital;
import co.edu.unicesar.rutavida.util.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogControllerTest {

    private CatalogTestSupport support;
    private CatalogController controller;

    @BeforeEach
    void setUp() {
        support = new CatalogTestSupport();
        controller = new CatalogController(support.service());
    }

    @Test
    void theEmergencyTypesComeFromTheService() {
        OperationResult<List<EmergencyType>> result = controller.emergencyTypes();

        assertTrue(result.success());
        assertEquals(CatalogTestSupport.EMERGENCY_TYPES, result.value());
    }

    @Test
    void theFleetCatalogsComeFromTheirDaos() {
        assertEquals(CatalogTestSupport.VEHICLE_TYPES, controller.vehicleTypes().value());
        assertEquals(CatalogTestSupport.UNITS, controller.units().value());
        assertEquals(CatalogTestSupport.STATIONS, controller.stations().value());
    }

    @Test
    void hospitalsAreTheOperationalOnesUnlessAllAreAsked() {
        List<Hospital> operational = controller.hospitals(false).value();
        List<Hospital> all = controller.hospitals(true).value();

        assertEquals(List.of(CatalogTestSupport.ACTIVE_HOSPITAL), operational);
        assertEquals(List.of(CatalogTestSupport.ACTIVE_HOSPITAL, CatalogTestSupport.INACTIVE_HOSPITAL), all);
    }

    @Test
    void oracleDownIsAnUnavailableErrorWithoutTechnicalDetails() {
        support.oracleDown.set(true);

        for (OperationResult<?> result : List.of(controller.emergencyTypes(), controller.units(),
                controller.stations(), controller.hospitals(false), controller.vehicleTypes())) {
            assertFalse(result.success());
            assertEquals(ErrorKind.UNAVAILABLE, result.error());
            assertEquals(Messages.DATABASE_DOWN, result.message());
        }
    }
}