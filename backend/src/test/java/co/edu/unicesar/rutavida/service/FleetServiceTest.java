
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.StationRequest;
import co.edu.unicesar.rutavida.dto.VehicleRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.FleetTestSupport.AMBULANCE_TYPE;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.AVAILABLE_UNIT;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.BASE;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.BUSY_UNIT;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.CLOSED_BASE;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.FIRE_TRUCK_TYPE;
import static co.edu.unicesar.rutavida.service.FleetTestSupport.VALLEDUPAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FleetService (CU-04) on in-memory DAOs, without Oracle. The data is in FleetTestSupport. */
class FleetServiceTest {

    private final FleetTestSupport support = new FleetTestSupport();
    private final FleetService service = support.fleetService();
    private final UserSession coordinator = support.sessionOf(COORDINATOR);

    private static VehicleRequest unit(long typeId, long stationId, String code, String plate) {
        return new VehicleRequest(typeId, stationId, code, plate);
    }

    private static StationRequest station(String name, String phone) {
        return new StationRequest(name, "Calle de EJEMPLO 3", "Barrio de EJEMPLO", phone, VALLEDUPAR);
    }

    // ------------------------------------------------------------------ units

    @Test
    void aUnitIsRegisteredAvailableWithoutAPositionAndAudited() {
        EmergencyVehicle unit = service.registerVehicle(
                unit(AMBULANCE_TYPE, BASE, " ejemplo-amb-03 ", "ejm103"), coordinator);

        assertEquals(3, unit.getId());
        assertEquals("EJEMPLO-AMB-03", unit.getUnitCode(), "trimmed and in upper case");
        assertEquals("EJM103", unit.getPlate());
        assertEquals(VehicleOperationalStatus.AVAILABLE, unit.getOperationalStatus());
        assertNull(unit.getLastLocation(),
                "its routes start at the station until the tracking sends a position");

        AuditLog audit = support.auditDao.last();
        assertEquals("UNIDADES", audit.getModule());
        assertEquals("CREAR", audit.getOperation());
        assertEquals(3L, audit.getRecordId());
        assertEquals(1, support.transactions.calls, "unit and audit together");
    }

    @Test
    void wrongUnitsAreRejectedWithAClearMessage() {
        assertThrows(ValidationException.class, () -> service.registerVehicle(
                unit(AMBULANCE_TYPE, BASE, " ", null), coordinator), "no code");

        assertThrows(ValidationException.class, () -> service.registerVehicle(
                unit(AMBULANCE_TYPE, BASE, "AMB 03", null), coordinator), "a space in the code");

        assertThrows(ValidationException.class, () -> service.registerVehicle(
                unit(AMBULANCE_TYPE, BASE, "EJEMPLO-AMB-03", "AB"), coordinator), "plate too short");

        assertThrows(NotFoundException.class, () -> service.registerVehicle(
                unit(9, BASE, "EJEMPLO-AMB-03", null), coordinator), "no such type");

        assertThrows(NotFoundException.class, () -> service.registerVehicle(
                unit(AMBULANCE_TYPE, 99, "EJEMPLO-AMB-03", null), coordinator), "no such station");

        assertThrows(BusinessRuleException.class, () -> service.registerVehicle(
                        unit(AMBULANCE_TYPE, CLOSED_BASE, "EJEMPLO-AMB-03", null), coordinator),
                "inactive station");

        assertEquals("Ya existe una unidad con el código EJEMPLO-AMB-01.",
                assertThrows(BusinessRuleException.class,
                        () -> service.registerVehicle(
                                unit(AMBULANCE_TYPE, BASE, "ejemplo-amb-01", null), coordinator))
                        .getMessage());

        assertEquals("Ya existe una unidad con la placa EJM101.",
                assertThrows(BusinessRuleException.class,
                        () -> service.registerVehicle(
                                unit(AMBULANCE_TYPE, BASE, "EJEMPLO-AMB-03", "ejm101"), coordinator))
                        .getMessage());

        assertEquals(2, service.listAll().size(), "nothing was saved");
    }

    @Test
    void editingKeepsTheStatusAndThePositionButAUnitOnAMissionIsNotEdited() {
        EmergencyVehicle edited = service.updateVehicle(AVAILABLE_UNIT,
                unit(FIRE_TRUCK_TYPE, BASE, "EJEMPLO-AMB-01", null), coordinator);

        assertEquals(FIRE_TRUCK_TYPE, service.findVehicle(AVAILABLE_UNIT).getVehicleTypeId());
        assertNull(service.findVehicle(AVAILABLE_UNIT).getPlate(), "the plate was removed");
        assertEquals(VehicleOperationalStatus.AVAILABLE, edited.getOperationalStatus());
        assertEquals(VALLEDUPAR, service.findVehicle(AVAILABLE_UNIT).getLastLocation());

        assertThrows(BusinessRuleException.class, () -> service.updateVehicle(BUSY_UNIT,
                unit(AMBULANCE_TYPE, BASE, "EJEMPLO-AMB-02", null), coordinator));

        assertThrows(NotFoundException.class, () -> service.updateVehicle(99,
                unit(AMBULANCE_TYPE, BASE, "EJEMPLO-AMB-09", null), coordinator));
    }

    @Test
    void changingTheAvailabilityIsAuditedAndTheCoordinatorsReceiveAnAlert() {
        EmergencyVehicle unit = service.changeVehicleStatus(AVAILABLE_UNIT,
                VehicleOperationalStatus.MAINTENANCE, coordinator);

        assertEquals(VehicleOperationalStatus.MAINTENANCE, unit.getOperationalStatus());
        assertEquals(VehicleOperationalStatus.MAINTENANCE,
                service.findVehicle(AVAILABLE_UNIT).getOperationalStatus());
        assertEquals("DISPONIBLE -> MANTENIMIENTO", support.auditDao.last().getDetail());

        List<Alert> alerts = support.alertDao.all();
        assertEquals(1, alerts.size(), "one coordinator can dispatch");
        assertEquals(COORDINATOR, alerts.get(0).getUserId());
        assertEquals(AlertType.AVAILABILITY_CHANGE, alerts.get(0).getType());
        assertTrue(alerts.get(0).getMessage().contains("MANTENIMIENTO"),
                alerts.get(0).getMessage());
        assertTrue(service.listAvailable().isEmpty(),
                "unit 2 is OCUPADO and unit 1 in MANTENIMIENTO");
    }

    @Test
    void ocupadoBelongsOnlyToTheMissions() {
        assertEquals("El estado OCUPADO solo lo pone el despacho.",
                assertThrows(BusinessRuleException.class,
                        () -> service.changeVehicleStatus(
                                AVAILABLE_UNIT, VehicleOperationalStatus.BUSY, coordinator))
                        .getMessage());

        assertThrows(BusinessRuleException.class, () -> service.changeVehicleStatus(
                        BUSY_UNIT, VehicleOperationalStatus.AVAILABLE, coordinator),
                "nobody frees a unit on a mission by hand");

        assertThrows(BusinessRuleException.class, () -> service.changeVehicleStatus(
                        AVAILABLE_UNIT, VehicleOperationalStatus.AVAILABLE, coordinator),
                "already DISPONIBLE");

        assertTrue(support.alertDao.all().isEmpty());
        assertTrue(support.auditDao.saved.isEmpty());
    }

    // ------------------------------------------------------------------ stations

    @Test
    void aStationIsRegisteredEditedAndDeactivatedOnlyWithoutWorkingUnits() {
        EmergencyStation created = service.registerStation(
                station(" EJEMPLO - Base norte ", null), coordinator);

        EmergencyStation edited = service.updateStation(created.getId(),
                station("EJEMPLO - Base norte", "605 5700000"), coordinator);

        assertEquals("EJEMPLO - Base norte", created.getName(), "trimmed");
        assertEquals(RecordStatus.ACTIVE, edited.getStatus());
        assertEquals("605 5700000", service.findStation(created.getId()).getPhone());

        assertEquals("Ya existe una estación con ese nombre.",
                assertThrows(BusinessRuleException.class,
                        () -> service.registerStation(
                                station("ejemplo - base norte", null), coordinator))
                        .getMessage());

        assertThrows(BusinessRuleException.class,
                () -> service.changeStationStatus(BASE, RecordStatus.INACTIVE, coordinator),
                "unit 1 is DISPONIBLE and unit 2 OCUPADO there");

        EmergencyStation closed = service.changeStationStatus(
                created.getId(), RecordStatus.INACTIVE, coordinator);

        assertEquals(RecordStatus.INACTIVE, closed.getStatus());
        assertEquals("ESTACIONES", support.auditDao.last().getModule());
        assertEquals("DESACTIVAR", support.auditDao.last().getOperation());

        assertEquals(RecordStatus.ACTIVE,
                service.changeStationStatus(CLOSED_BASE, RecordStatus.ACTIVE, coordinator)
                        .getStatus());

        assertThrows(BusinessRuleException.class,
                () -> service.changeStationStatus(CLOSED_BASE, RecordStatus.ACTIVE, coordinator),
                "already active");
    }

    @Test
    void aUnitIsAvailableOnlyAtAnActiveStationAndWrongStationsAreRejected() {
        EmergencyStation north = service.registerStation(
                station("EJEMPLO - Base norte", null), coordinator);

        EmergencyVehicle unit = service.registerVehicle(
                unit(AMBULANCE_TYPE, north.getId(), "EJEMPLO-AMB-03", null), coordinator);

        service.changeVehicleStatus(
                unit.getId(), VehicleOperationalStatus.OUT_OF_SERVICE, coordinator);

        service.changeStationStatus(north.getId(), RecordStatus.INACTIVE, coordinator);

        assertThrows(BusinessRuleException.class,
                () -> service.changeVehicleStatus(
                        unit.getId(), VehicleOperationalStatus.AVAILABLE, coordinator));

        assertThrows(ValidationException.class,
                () -> service.registerStation(station("X", "teléfono"), coordinator));

        assertThrows(ValidationException.class,
                () -> service.registerStation(
                        new StationRequest("X", "Calle", null, null, null), coordinator),
                "no location");

        assertThrows(NotFoundException.class,
                () -> service.changeStationStatus(99, RecordStatus.INACTIVE, coordinator));
    }
}
