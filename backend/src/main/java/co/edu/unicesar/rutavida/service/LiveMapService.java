package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AlertDAO;
import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.dto.SimulationState;
import co.edu.unicesar.rutavida.dto.TrackedMission;
import co.edu.unicesar.rutavida.dto.TrackedUnit;
import co.edu.unicesar.rutavida.dto.TrackingSnapshot;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.VehicleType;
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
    private final BooleanSupplier simulationRunning;
    private final Clock clock;

    /**
     * @param simulationRunning true while the simulation moves the units; the simulation
     *                          engine (K9) gives it, and until then it is always false
     */
    public LiveMapService(EmergencyVehicleDAO vehicleDao, VehicleTypeDAO vehicleTypeDao,
                          EmergencyAssignmentDAO assignmentDao, RouteDAO routeDao, AlertDAO alertDao,
                          BooleanSupplier simulationRunning, Clock clock) {
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.assignmentDao = Objects.requireNonNull(assignmentDao, "assignmentDao");
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.alertDao = Objects.requireNonNull(alertDao, "alertDao");
        this.simulationRunning = Objects.requireNonNull(simulationRunning, "simulationRunning");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The whole state of the map now, for the user of the session. */
    public TrackingSnapshot snapshot(UserSession session) {
        ValidationUtil.requireNonNull(session, "sesión");

        Map<Long, String> typeNames = vehicleTypeDao.findAll().stream()
                .collect(Collectors.toMap(VehicleType::getId, VehicleType::getName));
        List<EmergencyVehicle> vehicles = vehicleDao.findAll();

        List<TrackedUnit> units = new ArrayList<>();
        for (EmergencyVehicle vehicle : vehicles) {
            units.add(toUnit(vehicle, typeNames.getOrDefault(vehicle.getVehicleTypeId(), "UNIDAD")));
        }

        Map<Long, Route> activeByVehicle = routeDao.findActive().stream()
                .collect(Collectors.toMap(Route::getVehicleId, Function.identity(), (first, second) -> second));
        List<TrackedMission> missions = new ArrayList<>();
        for (EmergencyVehicle vehicle : vehicles) {
            if (vehicle.getOperationalStatus() == VehicleOperationalStatus.BUSY) {
                assignmentDao.findOpenByVehicle(vehicle.getId())
                        .ifPresent(mission -> missions.add(toMission(mission, activeByVehicle)));
            }
        }

        int unread = alertDao.findUnreadByUser(session.user().getId()).size();
        return new TrackingSnapshot(LocalDateTime.now(clock),
                new SimulationState(simulationRunning.getAsBoolean(), PositionSource.SIMULATION),
                units, missions, unread);
    }

    private static TrackedUnit toUnit(EmergencyVehicle vehicle, String typeName) {
        boolean located = vehicle.getLastLocation() != null;
        return new TrackedUnit(vehicle.getId(), vehicle.getUnitCode(), typeName,
                vehicle.getOperationalStatus(), vehicle.getLastLocation(), vehicle.getLastLocationAt(),
                located ? PositionSource.SIMULATION : null);
    }

    private TrackedMission toMission(EmergencyAssignment mission, Map<Long, Route> activeByVehicle) {
        Long activeRouteId = Optional.ofNullable(activeByVehicle.get(mission.getVehicleId()))
                .filter(route -> route.getEmergencyId() == mission.getEmergencyId())
                .map(Route::getId)
                .orElse(null);
        int version = (int) routeDao.findByEmergency(mission.getEmergencyId()).stream()
                .filter(route -> route.getVehicleId() == mission.getVehicleId())
                .count();
        return new TrackedMission(mission.getId(), mission.getEmergencyId(), mission.getVehicleId(),
                mission.getStatus(), activeRouteId, version);
    }
}
