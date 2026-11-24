package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AlertDAO;
import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.dao.VehicleLocationDAO;
import co.edu.unicesar.rutavida.dto.SimulationState;
import co.edu.unicesar.rutavida.dto.TrackedMission;
import co.edu.unicesar.rutavida.dto.TrackedUnit;
import co.edu.unicesar.rutavida.dto.TrackingSnapshot;
import co.edu.unicesar.rutavida.dto.RouteProgress;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

/**
 * What the live map shows (CU-07, CU-18, docs/api.md 8.1): where every unit is, the open
 * missions with the route they follow, and how many alerts the user has not read.
 *
 * <p>The web asks for it every 5 seconds, so it reads little: all the units and types in two
 * queries, the active routes in one, and the mission and routes only of the units OCUPADO.
 * It only reads; the positions are written by TrackingService when the simulation (K9) or a
 * real GPS reports them.
 */
public class LiveMapService {

    private final EmergencyVehicleDAO vehicleDao;
    private final VehicleTypeDAO vehicleTypeDao;
    private final EmergencyAssignmentDAO assignmentDao;
    private final RouteDAO routeDao;
    private final AlertDAO alertDao;
    private final VehicleLocationDAO locationDao;
    private final RouteService routeService;
    private final BooleanSupplier simulationRunning;
    private final Clock clock;
    private final LongPredicate routeUnavailable;

    /**
     * @param simulationRunning true while the simulation moves the units; the simulation
     *                          engine (K9) gives it, and until then it is always false
     */
    public LiveMapService(EmergencyVehicleDAO vehicleDao, VehicleTypeDAO vehicleTypeDao,
                          EmergencyAssignmentDAO assignmentDao, RouteDAO routeDao, AlertDAO alertDao,
                          BooleanSupplier simulationRunning, Clock clock) {
        this(vehicleDao, vehicleTypeDao, assignmentDao, routeDao, alertDao, null, simulationRunning, clock);
    }

    /** With the history DAO, the map distinguishes real GPS from simulation and exposes its telemetry. */
    public LiveMapService(EmergencyVehicleDAO vehicleDao, VehicleTypeDAO vehicleTypeDao,
                          EmergencyAssignmentDAO assignmentDao, RouteDAO routeDao, AlertDAO alertDao,
                          VehicleLocationDAO locationDao, BooleanSupplier simulationRunning, Clock clock) {
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.assignmentDao = Objects.requireNonNull(assignmentDao, "assignmentDao");
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.alertDao = Objects.requireNonNull(alertDao, "alertDao");
        this.locationDao = locationDao;
        this.routeService = null;
        this.simulationRunning = Objects.requireNonNull(simulationRunning, "simulationRunning");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.routeUnavailable = vehicleId -> false;
    }

    /** Enables remaining GPS distance/ETA without changing legacy map construction. */
    public LiveMapService(EmergencyVehicleDAO vehicleDao, VehicleTypeDAO vehicleTypeDao,
                          EmergencyAssignmentDAO assignmentDao, RouteDAO routeDao, AlertDAO alertDao,
                          VehicleLocationDAO locationDao, RouteService routeService,
                          BooleanSupplier simulationRunning, Clock clock) {
        this(vehicleDao, vehicleTypeDao, assignmentDao, routeDao, alertDao, locationDao, routeService,
                simulationRunning, clock, vehicleId -> false);
    }

    public LiveMapService(EmergencyVehicleDAO vehicleDao, VehicleTypeDAO vehicleTypeDao,
                          EmergencyAssignmentDAO assignmentDao, RouteDAO routeDao, AlertDAO alertDao,
                          VehicleLocationDAO locationDao, RouteService routeService,
                          BooleanSupplier simulationRunning, Clock clock, LongPredicate routeUnavailable) {
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.assignmentDao = Objects.requireNonNull(assignmentDao, "assignmentDao");
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.alertDao = Objects.requireNonNull(alertDao, "alertDao");
        this.locationDao = Objects.requireNonNull(locationDao, "locationDao");
        this.routeService = Objects.requireNonNull(routeService, "routeService");
        this.simulationRunning = Objects.requireNonNull(simulationRunning, "simulationRunning");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.routeUnavailable = Objects.requireNonNull(routeUnavailable, "routeUnavailable");
    }

    /** The whole state of the map now, for the user of the session. */
    public TrackingSnapshot snapshot(UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        Map<Long, String> typeNames = vehicleTypeDao.findAll().stream()
                .collect(Collectors.toMap(VehicleType::getId, VehicleType::getName));
        List<EmergencyVehicle> vehicles = vehicleDao.findAll();
        Map<Long, VehicleLocation> latest = locationDao == null ? Map.of() : locationDao.findLatestByVehicle();

        List<TrackedUnit> units = new ArrayList<>();
        for (EmergencyVehicle vehicle : vehicles) {
            units.add(toUnit(vehicle, typeNames.getOrDefault(vehicle.getVehicleTypeId(), "UNIDAD"),
                    latest.get(vehicle.getId())));
        }

        Map<Long, Route> activeByVehicle = routeDao.findActive().stream()
                .collect(Collectors.toMap(Route::getVehicleId, Function.identity(), (first, second) -> second));
        List<TrackedMission> missions = new ArrayList<>();
        for (EmergencyVehicle vehicle : vehicles) {
            if (vehicle.getOperationalStatus() == VehicleOperationalStatus.BUSY) {
                assignmentDao.findOpenByVehicle(vehicle.getId())
                        .ifPresent(mission -> missions.add(toMission(mission, vehicle, typeNames, activeByVehicle)));
            }
        }

        int unread = alertDao.findUnreadByUser(session.user().getId()).size();
        return new TrackingSnapshot(LocalDateTime.now(clock),
                new SimulationState(simulationRunning.getAsBoolean(), PositionSource.SIMULATION),
                units, missions, unread);
    }

    private static TrackedUnit toUnit(EmergencyVehicle vehicle, String typeName, VehicleLocation latest) {
        boolean located = vehicle.getLastLocation() != null;
        return new TrackedUnit(vehicle.getId(), vehicle.getUnitCode(), typeName,
                vehicle.getOperationalStatus(), vehicle.getLastLocation(), vehicle.getLastLocationAt(),
                located ? (latest == null ? PositionSource.SIMULATION : latest.getSource()) : null,
                latest == null ? null : latest.getSpeedKmh(),
                latest == null ? null : latest.getHeadingDegrees());
    }

    /** Metrics for the driver's own mission using the same projection as the coordinator. */
    TrackedMission missionOf(EmergencyAssignment assignment, EmergencyVehicle vehicle) {
        Map<Long, String> types = vehicleTypeDao.findAll().stream()
                .collect(Collectors.toMap(VehicleType::getId, VehicleType::getName));
        Map<Long, Route> routes = routeDao.findByEmergency(assignment.getEmergencyId()).stream()
                .filter(route -> route.getVehicleId() == vehicle.getId())
                .filter(route -> route.getStatus() == co.edu.unicesar.rutavida.model.enums.RouteStatus.ACTIVE)
                .findFirst().map(route -> Map.of(vehicle.getId(), route)).orElseGet(Map::of);
        return toMission(assignment, vehicle, types, routes);
    }

    private TrackedMission toMission(EmergencyAssignment mission, EmergencyVehicle vehicle,
                                     Map<Long, String> typeNames, Map<Long, Route> activeByVehicle) {
        Route active = Optional.ofNullable(activeByVehicle.get(mission.getVehicleId()))
                .filter(route -> route.getEmergencyId() == mission.getEmergencyId()).orElse(null);
        Long activeRouteId = active == null ? null : active.getId();
        int version = (int) routeDao.findByEmergency(mission.getEmergencyId()).stream()
                .filter(route -> route.getVehicleId() == mission.getVehicleId())
                .count();
        co.edu.unicesar.rutavida.model.GeoPoint destination = null;
        Double distance = null;
        Double eta = null;
        if (active != null && routeService != null && vehicle.getLastLocation() != null) {
            try {
                destination = routeService.destinationOf(active);
                RouteProgress progress = routeService.remainingProgress(active, vehicle.getLastLocation(),
                        VehicleProfile.forType(typeNames.getOrDefault(vehicle.getVehicleTypeId(), "UNIDAD")))
                        .orElse(null);
                if (progress != null) { distance = progress.distanceKm(); eta = progress.etaMinutes(); }
                else { distance = active.getDistanceKm(); eta = active.getEstimatedMinutes(); }
            } catch (RuntimeException ignored) {
                // A map metric must not hide the rest of the live map if an old route is incomplete.
            }
        }
        return new TrackedMission(mission.getId(), mission.getEmergencyId(), mission.getVehicleId(),
                mission.getStatus(), activeRouteId, version, destination, distance, eta,
                active != null && routeUnavailable.test(vehicle.getId()));
    }
}
