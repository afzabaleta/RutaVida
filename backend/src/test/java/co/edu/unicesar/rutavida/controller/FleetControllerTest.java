
package co.edu.unicesar.rutavida.controller;

import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.StationRequest;
import co.edu.unicesar.rutavida.dto.VehicleRequest;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.FleetTestSupport;
import org.junit.jupiter.api.Test;

import static co.edu.unicesar.rutavida.service.FleetTestSupport.AMBULANCE_TYPE;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.AVAILABLE_UNIT;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.BASE;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.BUSY_UNIT;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.OPERATOR;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.VALLEDUPAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FleetController with the real service on in-memory DAOs, without Oracle. */
class FleetControllerTest {

    private static final VehicleRequest NEW_UNIT =
            new VehicleRequest(AMBULANCE_TYPE, BASE, "EJEMPLO-AMB-03", "EJM103");

    private static final StationRequest NEW_STATION =
            new StationRequest("EJEMPLO - Base norte", "Calle de EJEMPLO 3",
                    null, null, VALLEDUPAR);

    private final FleetTestSupport support = new FleetTestSupport();
    private final SessionContext session = new SessionContext();

    private final FleetController controller =
            new FleetController(support.fleetService(), support.authService(), session);

    @Test
    void aCoordinatorManagesUnitsAndStations() {
        session.start(support.sessionOf(COORDINATOR));

        OperationResult<EmergencyVehicle> registered =
                controller.registerVehicle(NEW_UNIT);

        OperationResult<EmergencyVehicle> maintenance =
                controller.changeVehicleStatus(
                        AVAILABLE_UNIT, VehicleOperationalStatus.MAINTENANCE);

        OperationResult<EmergencyStation> station =
                controller.registerStation(NEW_STATION);

        assertTrue(registered.success(), registered.message());
        assertEquals(VehicleOperationalStatus.MAINTENANCE,
                maintenance.value().getOperationalStatus());
        assertTrue(station.success(), station.message());
        assertEquals(3, controller.listAll().value().size());

        assertEquals(RecordStatus.INACTIVE,
                controller.changeStationStatus(
                                station.value().getId(), RecordStatus.INACTIVE)
                        .value().getStatus());
    }

    @Test
    void anOperatorSeesButDoesNotManage() {
        assertEquals(ErrorKind.AUTHENTICATION,
                controller.listAll().error(), "without a session");

        session.start(support.sessionOf(OPERATOR));

        assertTrue(controller.listAll().success());
        assertEquals(2, controller.listStations().value().size());

        assertEquals(ErrorKind.PERMISSION,
                controller.registerVehicle(NEW_UNIT).error());

        assertEquals(ErrorKind.PERMISSION,
                controller.changeVehicleStatus(
                                AVAILABLE_UNIT, VehicleOperationalStatus.MAINTENANCE)
                        .error());

        assertEquals(ErrorKind.PERMISSION,
                controller.changeStationStatus(
                        BASE, RecordStatus.INACTIVE).error());
    }

    @Test
    void eachErrorKeepsItsKind() {
        session.start(support.sessionOf(COORDINATOR));

        assertEquals(ErrorKind.BUSINESS_RULE,
                controller.changeVehicleStatus(
                        BUSY_UNIT, VehicleOperationalStatus.AVAILABLE).error());

        assertEquals(ErrorKind.NOT_FOUND,
                controller.updateVehicle(99, NEW_UNIT).error());

        assertEquals(ErrorKind.VALIDATION,
                controller.registerVehicle(
                                new VehicleRequest(AMBULANCE_TYPE, BASE, " ", null))
                        .error());

        assertEquals(ErrorKind.NOT_FOUND,
                controller.updateStation(99, NEW_STATION).error());
    }
}
