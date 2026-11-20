package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.dao.EmergencyVehicleDAO;
import co.edu.unicesar.rutavida.dao.VehicleTypeDAO;
import co.edu.unicesar.rutavida.graph.AStarRouter;
import co.edu.unicesar.rutavida.graph.CostFunction;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.TrackingService;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Police patrols on their rounds (DT-10). Every cycle of the simulation, each POLICIA unit
 * that is DISPONIBLE and not moving starts a new round: a random point of the road network
 * between {@code minMeters} and {@code maxMeters} away, reached by the route engine (A*).
 * When it arrives it chooses another one, so the patrols never stay still while the
 * simulation runs. A patrol that stops being DISPONIBLE (for example, MANTENIMIENTO) stops.
 *
 * <p>The rounds are not saved as routes (RUTA is for missions): only the positions, through
 * its own {@link TrackingService}, marked SIMULACION like every simulated position. It uses
 * its own {@link VehicleMovementSimulator}, so it never touches the units on a mission.
 */
public class PatrolSimulator {

    private static final Logger LOG = Logger.getLogger(PatrolSimulator.class.getName());

    /** Name of the vehicle type of the patrols (07_boton_panico.sql). */
    public static final String PATROL_TYPE = "POLICIA";
    /** In the city: rounds of 0.8 to 3 km, about 1 to 4 minutes at 50 km/h. */
    public static final double DEFAULT_MIN_METERS = 800;
    public static final double DEFAULT_MAX_METERS = 3000;
    private static final int TRIES = 12;

    private final RoadGraph graph;
    private final VehicleMovementSimulator movement;
    private final TrackingService tracking;
    private final EmergencyVehicleDAO vehicleDao;
    private final VehicleTypeDAO typeDao;
    private final CostFunction cost;
    private final Random random;
    private final double minMeters;
    private final double maxMeters;
    private final AStarRouter router = new AStarRouter();
    private final Set<Long> patrolling = new HashSet<>();

    /**
     * @param movement the movement of the patrols only (not the one of the missions)
     * @param tracking saves the positions that {@code movement} produces
     */
    public PatrolSimulator(RoadGraph graph, VehicleMovementSimulator movement, TrackingService tracking,
                           EmergencyVehicleDAO vehicleDao, VehicleTypeDAO typeDao, CostFunction cost,
                           Random random, double minMeters, double maxMeters) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.movement = Objects.requireNonNull(movement, "movement");
        this.tracking = Objects.requireNonNull(tracking, "tracking");
        this.vehicleDao = Objects.requireNonNull(vehicleDao, "vehicleDao");
        this.typeDao = Objects.requireNonNull(typeDao, "typeDao");
        this.cost = Objects.requireNonNull(cost, "cost");
        this.random = Objects.requireNonNull(random, "random");
        if (!(minMeters >= 0 && maxMeters > minMeters)) {
            throw new IllegalArgumentException("Invalid round length: " + minMeters + " to " + maxMeters);
        }
        this.minMeters = minMeters;
        this.maxMeters = maxMeters;
    }

    /** One cycle: free patrols start a round, the others keep moving, and the positions are saved. */
    public synchronized void tick() {
        Optional<Long> patrolType = typeDao.findAll().stream()
                .filter(type -> PATROL_TYPE.equals(type.getName()))
                .map(VehicleType::getId)
                .findFirst();
        if (patrolType.isEmpty()) {
            return;   // A database without 07_boton_panico.sql has no patrols.
        }

        Set<Long> free = new HashSet<>();
        for (EmergencyVehicle unit : vehicleDao.findAll()) {
            if (unit.getVehicleTypeId() == patrolType.get()
                    && unit.getOperationalStatus() == VehicleOperationalStatus.AVAILABLE) {
                free.add(unit.getId());
                if (!movement.isFollowing(unit.getId())) {
                    startRound(unit);
                }
            }
        }

        for (Long vehicleId : Set.copyOf(patrolling)) {
            if (!free.contains(vehicleId)) {
                movement.stopFollowing(vehicleId);
                patrolling.remove(vehicleId);
            }
        }

        tracking.refreshPositions();
    }

    /** After a pause of the simulation, the stopped time does not count (like the missions). */
    public synchronized void resume() {
        movement.resume();
    }

    /** True while the unit is on a round. */
    public synchronized boolean isPatrolling(long vehicleId) {
        return patrolling.contains(vehicleId) && movement.isFollowing(vehicleId);
    }

    /** A random destination at a round's distance, reachable by the road network. */
    private void startRound(EmergencyVehicle unit) {
        GeoPoint here = unit.getLastLocation();
        if (here == null || graph.nodeCount() < 2) {
            return;
        }
        int from = graph.nearestNode(here.latitude(), here.longitude());
        Node start = graph.node(from);
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int to = random.nextInt(graph.nodeCount());
            double meters = start.distanceMetersTo(graph.node(to));
            if (to == from || meters < minMeters || meters > maxMeters) {
                continue;
            }
            Optional<RouteResult> round = router.route(graph, from, to, VehicleProfile.PATROL, cost, null, true);
            if (round.isPresent() && !round.get().edgeIds().isEmpty()) {
                movement.followPath(unit.getId(), round.get().edgeIds(), VehicleProfile.PATROL);
                patrolling.add(unit.getId());
                return;
            }
        }
        LOG.fine("No round found this cycle for unit " + unit.getUnitCode());
    }
}
