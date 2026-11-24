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

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves a real phone location only for the unit explicitly assigned to the signed-in driver.
 * A GPS fix preserves the existing routing engine, but it may ask the route monitor to replace
 * an active mission route when the phone is materially outside its geometry.
 */
public final class GpsTrackingService {

    private static final double MAX_SPEED_KMH = 250;
    /** GPS accuracy normally varies by several metres; recalculate only after a material deviation. */
    public static final double DEVIATION_THRESHOLD_METERS = 40;
    /** Prevent a noisy device from repeatedly replacing the same route. */
    public static final Duration RECALCULATION_COOLDOWN = Duration.ofSeconds(20);

    private final UnitDriverDAO drivers;
    private final EmergencyVehicleDAO vehicles;
    private final VehicleLocationDAO locations;
    private final TransactionManager transactions;
    private final Clock clock;
    private final double deviationThresholdMeters;
    private final Duration recalculationCooldown;
    private RouteMonitor routeMonitor;
    private final Map<Long, LocalDateTime> lastRecalculation = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, DriverFix> latestFixes = new ConcurrentHashMap<>();
    private final Set<Long> routeWarnings = ConcurrentHashMap.newKeySet();

    private record DriverFix(long userId, GpsPosition position) { }

    /** Only fixes accepted by this running server qualify, and a manual stop invalidates them. */
    public Optional<GeoPoint> freshLocation(long vehicleId, long userId, Duration maxAge) {
        DriverFix fix = latestFixes.get(vehicleId);
        if (fix == null || fix.userId() != userId) return Optional.empty();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime recorded = fix.position().updatedAt();
        return recorded.isAfter(now) || recorded.isBefore(now.minus(maxAge))
                ? Optional.empty() : Optional.of(fix.position().location());
    }

    public void stopReporting(long vehicleId) {
        latestFixes.remove(vehicleId);
    }

    public boolean routeUnavailable(long vehicleId) {
        return routeWarnings.contains(vehicleId);
    }

    void clearRouteWarning(long vehicleId) {
        routeWarnings.remove(vehicleId);
        lastRecalculation.remove(vehicleId);
    }

    public GpsTrackingService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                              VehicleLocationDAO locations,
                              TransactionManager transactions, Clock clock) {
        this(drivers, vehicles, locations, transactions, clock,
                DEVIATION_THRESHOLD_METERS, RECALCULATION_COOLDOWN);
    }

    private GpsTrackingService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                               VehicleLocationDAO locations,
                               TransactionManager transactions, Clock clock,
                               double deviationThresholdMeters, Duration recalculationCooldown) {
        this.drivers = Objects.requireNonNull(drivers, "drivers");
        this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
        this.locations = Objects.requireNonNull(locations, "locations");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!Double.isFinite(deviationThresholdMeters) || deviationThresholdMeters <= 0) {
            throw new IllegalArgumentException("deviationThresholdMeters must be positive");
        }
        this.deviationThresholdMeters = deviationThresholdMeters;
        this.recalculationCooldown = Objects.requireNonNull(recalculationCooldown, "recalculationCooldown");
        if (recalculationCooldown.isNegative() || recalculationCooldown.isZero()) {
            throw new IllegalArgumentException("recalculationCooldown must be positive");
        }
        this.routeMonitor = null;
    }

    /** Enables deviation-aware recalculation while preserving the original GPS-only constructor for tests. */
    public GpsTrackingService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                              VehicleLocationDAO locations, TransactionManager transactions, Clock clock,
                              RouteMonitor routeMonitor) {
        this(drivers, vehicles, locations, transactions, clock, routeMonitor,
                DEVIATION_THRESHOLD_METERS, RECALCULATION_COOLDOWN);
    }

    /** Uses a threshold supplied by the deployment configuration (30–50 m in RutaVida). */
    public GpsTrackingService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                              VehicleLocationDAO locations, TransactionManager transactions, Clock clock,
                              RouteMonitor routeMonitor, double deviationThresholdMeters) {
        this(drivers, vehicles, locations, transactions, clock, routeMonitor,
                deviationThresholdMeters, RECALCULATION_COOLDOWN);
    }

    private GpsTrackingService(UnitDriverDAO drivers, EmergencyVehicleDAO vehicles,
                               VehicleLocationDAO locations, TransactionManager transactions, Clock clock,
                               RouteMonitor routeMonitor, double deviationThresholdMeters,
                               Duration recalculationCooldown) {
        this(drivers, vehicles, locations, transactions, clock,
                deviationThresholdMeters, recalculationCooldown);
        this.routeMonitor = Objects.requireNonNull(routeMonitor, "routeMonitor");
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

        GpsPosition stored = transactions.callInTransaction(() -> {
            vehicle.setLastLocation(location);
            vehicle.setLastLocationAt(now);
            vehicles.updateLocation(vehicle.getId(), location, now);
            locations.insertBatch(List.of(new VehicleLocation(0, vehicle.getId(), now, location,
                    speed, heading, PositionSource.GPS)));
            return new GpsPosition(vehicle.getId(), location, now, speed, heading, PositionSource.GPS, false);
        });
        latestFixes.put(vehicle.getId(), new DriverFix(driver.user().getId(), stored));
        boolean recalculated = recalculateIfNeeded(vehicle.getId(), location, now);
        return recalculated
                ? new GpsPosition(stored.vehicleId(), stored.location(), stored.updatedAt(), stored.speedKmh(),
                stored.headingDegrees(), stored.source(), true)
                : stored;
    }

    private boolean recalculateIfNeeded(long vehicleId, GeoPoint location, LocalDateTime now) {
        if (routeMonitor == null) return false;
        LocalDateTime previous = lastRecalculation.get(vehicleId);
        if (previous != null && Duration.between(previous, now).compareTo(recalculationCooldown) < 0) {
            return false;
        }
        boolean recalculated;
        try {
            recalculated = routeMonitor.recalculateAfterGpsDeviation(vehicleId, location,
                    deviationThresholdMeters).isPresent();
            routeWarnings.remove(vehicleId);
        } catch (BusinessRuleException outsideNetwork) {
            // Keep the assigned mission and its previous route. Both views read the warning.
            routeWarnings.add(vehicleId);
            lastRecalculation.put(vehicleId, now);
            return false;
        }
        if (recalculated) lastRecalculation.put(vehicleId, now);
        return recalculated;
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
