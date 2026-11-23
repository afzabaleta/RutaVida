package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.UnitDriverDAO;
import co.edu.unicesar.rutavida.dto.GpsPositionRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real GPS updates are isolated from routing and always bound to one assigned driver. */
class GpsTrackingServiceTest {

    private static final long DRIVER_ID = 44;
    private final InMemoryEmergencyVehicleDAO vehicles = new InMemoryEmergencyVehicleDAO();
    private final InMemoryVehicleLocationDAO history = new InMemoryVehicleLocationDAO();
    private final EmergencyTestSupport.CountingTransactions transactions =
            new EmergencyTestSupport.CountingTransactions();
    private final Drivers drivers = new Drivers();
    private final GpsTrackingService service = new GpsTrackingService(drivers, vehicles, history,
            transactions, EmergencyTestSupport.CLOCK);

    @Test
    void assignedDriverSavesGpsPositionWithTelemetry() {
        long vehicle = vehicles.insert(new EmergencyVehicle(0, 1, 1, "AMB-REAL-01", "ABC123",
                VehicleOperationalStatus.AVAILABLE, null, null));
        drivers.vehicle = vehicle;

        var saved = service.record(driver(), new GpsPositionRequest(new GeoPoint(10.4631, -73.2532), 38.4, 91.0));

        assertEquals(vehicle, saved.vehicleId());
        assertEquals(PositionSource.GPS, saved.source());
        assertEquals(new GeoPoint(10.4631, -73.2532), vehicles.findById(vehicle).orElseThrow().getLastLocation());
        assertEquals(1, history.saved.size());
        assertEquals(38.4, history.saved.get(0).getSpeedKmh());
        assertEquals(91.0, history.saved.get(0).getHeadingDegrees());
        assertEquals(PositionSource.GPS, history.saved.get(0).getSource());
        assertEquals(1, transactions.calls);
    }

    @Test
    void unassignedDriverAndInvalidTelemetryAreRejected() {
        assertThrows(BusinessRuleException.class,
                () -> service.record(driver(), new GpsPositionRequest(new GeoPoint(10.46, -73.25), null, null)));

        long vehicle = vehicles.insert(new EmergencyVehicle(0, 1, 1, "AMB-REAL-02", null,
                VehicleOperationalStatus.AVAILABLE, null, null));
        drivers.vehicle = vehicle;
        assertThrows(ValidationException.class,
                () -> service.record(driver(), new GpsPositionRequest(new GeoPoint(10.46, -73.25), -1.0, null)));
        assertThrows(ValidationException.class,
                () -> service.record(driver(), new GpsPositionRequest(new GeoPoint(10.46, -73.25), null, 360.0)));
    }

    private static UserSession driver() {
        User user = new User(DRIVER_ID, 4, "1065000044", "Ana", "Conductor", "ana@ejemplo.com",
                "ana.conductor", "hash", RecordStatus.ACTIVE, LocalDateTime.now(EmergencyTestSupport.CLOCK));
        Role role = new Role(4, "BOMBERO_PARAMEDICO", "Prueba", RecordStatus.ACTIVE, Set.of());
        return new UserSession(user, role, LocalDateTime.now(EmergencyTestSupport.CLOCK));
    }

    private static final class Drivers implements UnitDriverDAO {
        long vehicle;

        @Override public OptionalLong findVehicleIdByUser(long userId) {
            return userId == DRIVER_ID && vehicle > 0 ? OptionalLong.of(vehicle) : OptionalLong.empty();
        }
        @Override public OptionalLong findUserIdByVehicle(long vehicleId) { return OptionalLong.empty(); }
        @Override public void assign(long userId, long vehicleId) { vehicle = vehicleId; }
        @Override public void removeByVehicle(long vehicleId) { if (vehicle == vehicleId) vehicle = 0; }
    }
}
