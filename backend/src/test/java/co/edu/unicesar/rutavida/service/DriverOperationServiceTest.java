package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.UnitDriverDAO;
import co.edu.unicesar.rutavida.dto.GpsPositionRequest;
import co.edu.unicesar.rutavida.dto.HospitalOption;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Field-driver lifecycle: arrival and finalization never turn the GPS session off. */
class DriverOperationServiceTest {

    private static final long DRIVER_ID = 44;

    @Test
    void arrivalTransferAndFinalizationKeepTheGpsLifecycleIndependent() {
        DispatchTestSupport city = new DispatchTestSupport();
        long emergencyId = city.report(DispatchTestSupport.MEDICAL, 1, 2);
        long assignmentId = city.service().dispatch(emergencyId, DispatchTestSupport.AMBULANCE,
                EmergencyTestSupport.session("DESPACHO:GESTIONAR")).assignment().getId();

        InMemoryAlertDAO alerts = new InMemoryAlertDAO();
        var links = new DriverLink(DispatchTestSupport.AMBULANCE);
        var locations = new InMemoryVehicleLocationDAO();
        var gps = new GpsTrackingService(links, city.vehicles, locations,
                city.transactions, EmergencyTestSupport.CLOCK);
        var routing = new RouteService(city.graph, city.routes, city.transactions, EmergencyTestSupport.CLOCK);
        var live = new LiveMapService(city.vehicles, () -> DispatchTestSupport.VEHICLE_TYPES,
                city.assignments, city.routes, alerts, locations, routing, () -> false, EmergencyTestSupport.CLOCK);
        DriverOperationService service = new DriverOperationService(
                links, city.vehicles, city.assignments,
                city.emergencies, city.service(),
                new AlertService(alerts, new InMemoryUserDAO(), new InMemoryRoleDAO(), city.transactions,
                        EmergencyTestSupport.CLOCK),
                new AuditService(city.audit, EmergencyTestSupport.CLOCK), city.transactions,
                EmergencyTestSupport.CLOCK, city.emergencyTypes, live, gps);

        assertThrows(BusinessRuleException.class, () -> service.confirmArrival(driver()));
        assertThrows(BusinessRuleException.class, () -> service.transferHospitals(driver(), assignmentId));
        gps.record(driver(), new GpsPositionRequest(DispatchTestSupport.at(1, 1), null, null));
        assertEquals(AssignmentStatus.EN_ROUTE, service.startGps(driver()).status());

        var onScene = service.confirmArrival(driver());
        assertEquals(AssignmentStatus.ON_SCENE, onScene.status());
        assertNotNull(onScene.arrivedAt());

        assertThrows(BusinessRuleException.class, () -> service.stopGps(driver()));
        assertEquals(AssignmentStatus.ON_SCENE, service.current(driver()).status());

        assertTrue(onScene.canTransfer());
        assertThrows(NotFoundException.class, () -> service.transferHospitals(driver(), assignmentId + 1));
        assertThrows(NotFoundException.class, () -> service.startTransfer(driver(), assignmentId + 1,
                DispatchTestSupport.CONFIRMED_HOSPITAL));

        List<HospitalOption> hospitals = service.transferHospitals(driver(), assignmentId);
        assertEquals(List.of(DispatchTestSupport.NEAR_UNCONFIRMED_HOSPITAL, DispatchTestSupport.CONFIRMED_HOSPITAL),
                hospitals.stream().map(HospitalOption::hospitalId).toList());
        assertThrows(BusinessRuleException.class, () -> service.startTransfer(driver(), assignmentId,
                DispatchTestSupport.INACTIVE_HOSPITAL));

        // The persisted display position can differ from the driver's latest real fix.
        city.vehicles.updateLocation(DispatchTestSupport.AMBULANCE, DispatchTestSupport.at(0, 1),
                LocalDateTime.now(EmergencyTestSupport.CLOCK));
        var transfer = service.startTransfer(driver(), assignmentId, DispatchTestSupport.CONFIRMED_HOSPITAL);
        assertEquals(AssignmentStatus.TRANSPORTING, transfer.status());
        assertEquals(EmergencyStatus.TRANSPORTING, city.emergencies.findById(emergencyId).orElseThrow().getStatus());
        Route route = service.currentRoutes(driver()).stream()
                .filter(item -> item.getStatus() == RouteStatus.ACTIVE).findFirst().orElseThrow();
        assertEquals(DispatchTestSupport.CONFIRMED_HOSPITAL, route.getDestinationHospitalId());
        assertEquals(0.44, route.getDistanceKm(), 0.01, "the route starts at the driver's last real GPS point");
        assertEquals(route.getId(), transfer.tracking().activeRouteId());
        assertEquals(route.getId(), live.snapshot(driver()).missions().getFirst().activeRouteId());
        assertNotNull(transfer.tracking().etaMinutes());
        assertThrows(BusinessRuleException.class, () -> service.startTransfer(driver(), assignmentId,
                DispatchTestSupport.CONFIRMED_HOSPITAL));

        gps.stopReporting(DispatchTestSupport.AMBULANCE);
        assertThrows(BusinessRuleException.class, () -> service.finishAttention(driver()));
        var hospital = DispatchTestSupport.at(0, 0);
        gps.record(driver(), new GpsPositionRequest(hospital, null, null));
        var finished = service.finishAttention(driver());
        assertEquals(AssignmentStatus.FINISHED, finished.status());
        assertNotNull(finished.finishedAt());
        assertEquals(AssignmentStatus.FINISHED,
                city.assignments.findById(assignmentId).orElseThrow().getStatus());
        assertEquals(VehicleOperationalStatus.AVAILABLE,
                city.vehicles.findById(DispatchTestSupport.AMBULANCE).orElseThrow().getOperationalStatus());
        assertEquals(EmergencyStatus.CLOSED, city.emergencies.findById(emergencyId).orElseThrow().getStatus());
        assertEquals(3, alerts.all().size(), "arrival, transfer and finalization notify the coordinator");
        assertTrue(live.snapshot(driver()).missions().isEmpty());
        assertTrue(service.currentRoutes(driver()).isEmpty());
        assertTrue(city.audit.saved.stream().anyMatch(log -> "CONFIRMAR_LLEGADA".equals(log.getOperation())));
        assertTrue(city.audit.saved.stream().anyMatch(log -> "FINALIZAR_ATENCION".equals(log.getOperation())
                && log.getDetail().contains("lat=" + hospital.latitude() + ", lon=" + hospital.longitude())));
        assertFalse(city.assignments.findOpenByVehicle(DispatchTestSupport.AMBULANCE).isPresent());
        assertTrue(gps.freshLocation(DispatchTestSupport.AMBULANCE, DRIVER_ID,
                PanicService.DEFAULT_GPS_MAX_AGE).isPresent());
        service.stopGps(driver());
        assertTrue(gps.freshLocation(DispatchTestSupport.AMBULANCE, DRIVER_ID,
                PanicService.DEFAULT_GPS_MAX_AGE).isEmpty());
    }

    private static UserSession driver() {
        User user = new User(DRIVER_ID, 4, "1065000044", "Ana", "Conductor", "ana@ejemplo.com",
                "ana.conductor", "hash", RecordStatus.ACTIVE, LocalDateTime.now(EmergencyTestSupport.CLOCK));
        Role role = new Role(4, "BOMBERO_PARAMEDICO", "Prueba", RecordStatus.ACTIVE, Set.of());
        return new UserSession(user, role, LocalDateTime.now(EmergencyTestSupport.CLOCK));
    }

    private record DriverLink(long vehicleId) implements UnitDriverDAO {
        @Override public OptionalLong findVehicleIdByUser(long userId) {
            return userId == DRIVER_ID ? OptionalLong.of(vehicleId) : OptionalLong.empty();
        }
        @Override public OptionalLong findUserIdByVehicle(long id) { return OptionalLong.empty(); }
        @Override public void assign(long userId, long id) { throw new UnsupportedOperationException(); }
        @Override public void removeByVehicle(long id) { throw new UnsupportedOperationException(); }
    }
}
