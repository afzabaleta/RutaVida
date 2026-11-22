package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recalculation of active routes (CU-17, rule RN-09). When the traffic or an incident
 * changes some segments, it looks for the ACTIVA routes that use them and, for each one,
 * calculates again from the current position of the unit:
 * <ul>
 *   <li>If a segment ahead is closed, the route is replaced.</li>
 *   <li>Otherwise, it is replaced only if the new one saves more than 15 % or more than
 *   30 s of the remaining time, so the route does not "blink" for small changes.</li>
 * </ul>
 * A replacement is saved in one transaction (the old routes DESCARTADA, the new ones ACTIVA
 * and PROPUESTA) and the coordinator of the mission receives an alert (S8).
 * The cycle of the simulation (K9) calls this class and makes the unit follow the new route.
 */
public class RouteMonitor {

    private static final Logger LOG = Logger.getLogger(RouteMonitor.class.getName());

    /** RN-09: initial values to calibrate with tests, not measured. */
    static final double MIN_IMPROVEMENT = 0.15;
    static final double MIN_SECONDS_SAVED = 30;

    private final RouteDAO routeDao;
    private final RouteService routeService;
    private final EmergencyVehicleDAO vehicleDao;
    private final VehicleTypeDAO vehicleTypeDao;
    private final EmergencyAssignmentDAO assignmentDao;
    private final AlertService alertService;
    private final TransactionManager transactions;

    public RouteMonitor(RouteDAO routeDao, RouteService routeService, EmergencyVehicleDAO vehicleDao,
                        VehicleTypeDAO vehicleTypeDao, EmergencyAssignmentDAO assignmentDao,
                        AlertService alertService, TransactionManager transactions) {
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.routeService = Objects.requireNonNull(routeService, "routeService");
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.vehicleTypeDao = Objects.requireNonNull(vehicleTypeDao, "vehicleTypeDao");
        this.assignmentDao = Objects.requireNonNull(assignmentDao, "assignmentDao");
        this.alertService = Objects.requireNonNull(alertService, "alertService");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /**
     * Checks the active routes that use any of the changed segments.
     *
     * @return the new ACTIVA routes, one per route that was replaced
     */
    public List<Route> checkActiveRoutes(Set<Long> changedSegmentIds) {
        if (changedSegmentIds == null || changedSegmentIds.isEmpty()) {
            return List.of();
        }
        List<Route> replaced = new ArrayList<>();
        for (Route route : routeDao.findActiveUsingSegments(changedSegmentIds)) {
            try {
                recalculate(route).ifPresent(replaced::add);
            } catch (RuntimeException e) {
                // One route that fails must not stop the others.
                LOG.log(Level.WARNING, "Route " + route.getId() + " could not be recalculated", e);
            }
        }
        return replaced;
    }

    /**
     * Calculates the route again from where the unit is now and replaces it if RN-09 says so.
     *
     * @return the new ACTIVA route, or empty if the current one is kept (or the unit has no
     * known position, or there is no other way)
     */
    public Optional<Route> recalculate(Route active) {
        Objects.requireNonNull(active, "route");
        if (active.getStatus() != RouteStatus.ACTIVE) {
            return Optional.empty();
        }
        EmergencyVehicle vehicle = vehicleDao.findById(active.getVehicleId())
                .orElseThrow(() -> new NotFoundException("Unidad", active.getVehicleId()));
        GeoPoint position = vehicle.getLastLocation();
        if (position == null) {
            return Optional.empty();
        }
        VehicleProfile profile = VehicleProfile.forType(typeName(vehicle));

        OptionalDouble remaining = routeService.remainingSeconds(active, position, profile);
        List<RouteOption> options;
        try {
            options = routeService.calculateOptions(position, routeService.destinationOf(active), profile);
        } catch (BusinessRuleException noOtherWay) {
            // Already at the destination, or no way with the current conditions: keep the route.
            return Optional.empty();
        }
        double newSeconds = options.get(0).estimatedMinutes() * 60;

        boolean blocked = remaining.isEmpty();
        if (!blocked && !shouldReplace(remaining.getAsDouble(), newSeconds)) {
            return Optional.empty();
        }
        double minutesSaved = blocked ? 0 : (remaining.getAsDouble() - newSeconds) / 60;

        return Optional.of(transactions.callInTransaction(() -> {
            for (Route old : missionRoutes(active)) {
                routeDao.updateStatus(old.getId(), RouteStatus.DISCARDED);
            }
            List<Route> saved = routeService.saveForMission(active.getEmergencyId(), active.getVehicleId(),
                    active.getDestinationHospitalId(), profile, options);
            missionOf(active).ifPresent(assignment ->
                    alertService.routeRecalculated(assignment, vehicle.getUnitCode(), minutesSaved));
            return saved.get(0);
        }));
    }

    /** RN-09: replace when the new route saves more than 15 % or more than 30 s. */
    static boolean shouldReplace(double remainingSeconds, double newSeconds) {
        double saved = remainingSeconds - newSeconds;
        return saved > MIN_SECONDS_SAVED || saved > remainingSeconds * MIN_IMPROVEMENT;
    }

    /** The ACTIVA and PROPUESTA routes of the same unit, emergency and destination. */
    private List<Route> missionRoutes(Route active) {
        return routeDao.findByEmergency(active.getEmergencyId()).stream()
                .filter(route -> route.getVehicleId() == active.getVehicleId())
                .filter(route -> Objects.equals(route.getDestinationHospitalId(), active.getDestinationHospitalId()))
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE || route.getStatus() == RouteStatus.PROPOSED)
                .toList();
    }

    private Optional<EmergencyAssignment> missionOf(Route route) {
        return assignmentDao.findOpenByEmergency(route.getEmergencyId()).stream()
                .filter(assignment -> assignment.getVehicleId() == route.getVehicleId())
                .findFirst();
    }

    private String typeName(EmergencyVehicle vehicle) {
        return vehicleTypeDao.findAll().stream()
                .filter(type -> type.getId() == vehicle.getVehicleTypeId())
                .map(VehicleType::getName)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Tipo de vehículo", vehicle.getVehicleTypeId()));
    }
}