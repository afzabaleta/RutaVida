package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.UnitDriverDAO;
import co.edu.unicesar.rutavida.dao.VehicleLocationDAO;
import co.edu.unicesar.rutavida.dto.DriverUnit;
import co.edu.unicesar.rutavida.dto.GpsPosition;
import co.edu.unicesar.rutavida.dto.GpsPositionRequest;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Saves a real phone location only for the unit explicitly assigned to the signed-in driver.
 * Route calculation is not involved: a GPS fix only updates the operational position.
 */
public final class GpsTrackingService {

    private static final double MAX_SPEED_KMH = 250;

    private final UnitDriverDAO drivers;
    private final EmergencyVehicleDAO vehicles;
    private final VehicleLocationDAO locations;
    private final TransactionManager transactions;
    private final Clock clock;

    public GpsTrackingService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                              VehicleLocationDAO locations,
                              TransactionManager transactions, Clock clock) {
        this.drivers = Objects.requireNonNull(drivers, "drivers");
        this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
        this.locations = Objects.requireNonNull(locations, "locations");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DriverUnit unitOf(UserSession driver) {
        EmergencyVehicle vehicle = assignedVehicle(driver);
        return new DriverUnit(vehicle.getId(), vehicle.getUnitCode(), vehicle.getPlate(),
                vehicle.getLastLocation(), vehicle.getLastLocationAt());
    }

    public GpsPosition record(UserSession driver, GpsPositionRequest request) {
        ValidationUtil.requireNonNull(request, "ubicación GPS");
        GeoPoint location = ValidationUtil.requireLocation(request.location());
        Double speed = validSpeed(request.speedKmh());
        Double heading = validHeading(request.headingDegrees());
        EmergencyVehicle vehicle = assignedVehicle(driver);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        return transactions.callInTransaction(() -> {
            // Real GPS is observational data. It must never invoke route recalculation or mission changes.
            vehicle.setLastLocation(location);
            vehicle.setLastLocationAt(now);
            vehicles.updateLocation(vehicle.getId(), location, now);
            locations.insertBatch(List.of(new VehicleLocation(0, vehicle.getId(), now, location,
                    speed, heading, PositionSource.GPS)));
            return new GpsPosition(vehicle.getId(), location, now, speed, heading, PositionSource.GPS);
        });
    }

    private EmergencyVehicle assignedVehicle(UserSession driver) {
        ValidationUtil.requireNonNull(driver, "sesión");
        long vehicleId = drivers.findVehicleIdByUser(driver.user().getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "No tienes una unidad asignada para compartir ubicación."));
        return vehicles.findById(vehicleId).orElseThrow(() -> new NotFoundException("Unidad", vehicleId));
    }

    private static Double validSpeed(Double value) {
        if (value == null) return null;
        if (!Double.isFinite(value) || value < 0 || value > MAX_SPEED_KMH) {
            throw new ValidationException("La velocidad GPS debe estar entre 0 y 250 km/h.");
        }
        return value;
    }

    private static Double validHeading(Double value) {
        if (value == null) return null;
        if (!Double.isFinite(value) || value < 0 || value >= 360) {
            throw new ValidationException("La dirección GPS debe estar entre 0 y 359 grados.");
        }
        return value;
    }
}
