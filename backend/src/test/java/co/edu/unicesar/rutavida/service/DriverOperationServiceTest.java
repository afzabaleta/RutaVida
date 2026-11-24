package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.UnitDriverDAO;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Field-driver lifecycle: arrival and finalization never turn the GPS session off. */
class DriverOperationServiceTest {

    private static final long DRIVER_ID = 44;

    @Test
    void arrivalAndFinalizationKeepTheGpsLifecycleIndependent() {
        DispatchTestSupport city = new DispatchTestSupport();
        long emergencyId = city.report(DispatchTestSupport.MEDICAL, 1, 2);
        long assignmentId = city.service().dispatch(emergencyId, DispatchTestSupport.AMBULANCE,
                EmergencyTestSupport.session("DESPACHO:GESTIONAR")).assignment().getId();

        InMemoryAlertDAO alerts = new InMemoryAlertDAO();
        DriverOperationService service = new DriverOperationService(
                new DriverLink(DispatchTestSupport.AMBULANCE), city.vehicles, city.assignments,
                city.emergencies, city.service(),
                new AlertService(alerts, new InMemoryUserDAO(), new InMemoryRoleDAO(), city.transactions,
                        EmergencyTestSupport.CLOCK),
                new AuditService(city.audit, EmergencyTestSupport.CLOCK), city.transactions,
                EmergencyTestSupport.CLOCK);

        assertEquals(AssignmentStatus.EN_ROUTE, service.startGps(driver()).status());

        var onScene = service.confirmArrival(driver());
        assertEquals(AssignmentStatus.ON_SCENE, onScene.status());
        assertNotNull(onScene.arrivedAt());

        // Recording an explicit stop is an audit-only action: it cannot undo the arrival.
        assertEquals(AssignmentStatus.ON_SCENE, service.stopGps(driver()).status());

        var finished = service.finishAttention(driver());
        assertEquals(AssignmentStatus.FINISHED, finished.status());
        assertNotNull(finished.finishedAt());
        assertEquals(AssignmentStatus.FINISHED,
                city.assignments.findById(assignmentId).orElseThrow().getStatus());
        assertEquals(VehicleOperationalStatus.AVAILABLE,
                city.vehicles.findById(DispatchTestSupport.AMBULANCE).orElseThrow().getOperationalStatus());
        assertEquals(EmergencyStatus.CLOSED, city.emergencies.findById(emergencyId).orElseThrow().getStatus());
        assertEquals(2, alerts.all().size(), "arrival and finalization notify the coordinator");
        assertTrue(city.audit.saved.stream().anyMatch(log -> "CONFIRMAR_LLEGADA".equals(log.getOperation())));
        assertTrue(city.audit.saved.stream().anyMatch(log -> "FINALIZAR_ATENCION".equals(log.getOperation())));
        assertFalse(city.assignments.findOpenByVehicle(DispatchTestSupport.AMBULANCE).isPresent());
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
