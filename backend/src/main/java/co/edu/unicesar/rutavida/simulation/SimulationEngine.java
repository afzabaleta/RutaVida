package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.service.DispatchService;
import co.edu.unicesar.rutavida.service.DispatchService.ActiveMission;
import co.edu.unicesar.rutavida.service.RoadConditionService;
import co.edu.unicesar.rutavida.service.RouteMonitor;
import co.edu.unicesar.rutavida.service.TrackingService;
import co.edu.unicesar.rutavida.service.TrafficRecorder;
import co.edu.unicesar.rutavida.traffic.EdgeUpdate;
import co.edu.unicesar.rutavida.traffic.SimulatedTrafficProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The cycle of the simulation (CU-19, K9). Every {@code tick} it:
 * <ol>
 *   <li>changes the traffic every {@code trafficEvery} (or at once, if the demo forced a
 *   segment), applies it to the graph and saves it in TRAFICO;</li>
 *   <li>puts the graph in line with the ACTIVO incidents;</li>
 *   <li>recalculates the active routes that use the segments that changed (K8);</li>
 *   <li>makes each unit follow the ACTIVA route of its mission: a new dispatch leaves
 *   (ASIGNADA → EN_RUTA) and a recalculated route replaces the old one;</li>
 *   <li>moves the units and saves their positions (A13);</li>
 *   <li>marks EN_SITIO the units that reached the end of their route to the scene;</li>
 *   <li>moves the police patrols on their rounds, if there are any (DT-10).</li>
 * </ol>
 * Each step runs even if another one fails, so one error does not stop the simulation.
 * Everything it produces is marked SIMULACION: no real GPS nor real traffic is used.
 */
public class SimulationEngine {

    private static final Logger LOG = Logger.getLogger(SimulationEngine.class.getName());

    public static final Duration DEFAULT_TICK = Duration.ofSeconds(2);
    public static final Duration DEFAULT_TRAFFIC_EVERY = Duration.ofSeconds(15);

    private final RoadGraph graph;
    private final SimulatedTrafficProvider traffic;
    private final RoadConditionService roadConditions;
    private final TrafficRecorder trafficRecorder;
    private final RouteMonitor routeMonitor;
    private final VehicleMovementSimulator movement;
    private final TrackingService tracking;
    private final DispatchService dispatch;
    private final Clock clock;
    private final Duration tick;
    private final Duration trafficEvery;
    /** The rounds of the patrols (DT-10); null when the engine has no patrols. */
    private final PatrolSimulator patrols;

    /** Unit → route it follows, unit → its mission, and the units already at the end of their route. */
    private final Map<Long, Long> following = new HashMap<>();
    private final Map<Long, EmergencyAssignment> missions = new HashMap<>();
    private final Set<Long> arrived = new HashSet<>();

    private ScheduledExecutorService scheduler;
    private LocalDateTime startedAt;
    private Instant lastTraffic;
    private boolean trafficForced;

    public SimulationEngine(RoadGraph graph, SimulatedTrafficProvider traffic, RoadConditionService roadConditions,
                            TrafficRecorder trafficRecorder, RouteMonitor routeMonitor,
                            VehicleMovementSimulator movement, TrackingService tracking, DispatchService dispatch,
                            Clock clock, Duration tick, Duration trafficEvery) {
        this(graph, traffic, roadConditions, trafficRecorder, routeMonitor, movement, tracking, dispatch,
                clock, tick, trafficEvery, null);
    }

    /** The same engine, also moving the police patrols on their rounds (DT-10). */
    public SimulationEngine(RoadGraph graph, SimulatedTrafficProvider traffic, RoadConditionService roadConditions,
                            TrafficRecorder trafficRecorder, RouteMonitor routeMonitor,
                            VehicleMovementSimulator movement, TrackingService tracking, DispatchService dispatch,
                            Clock clock, Duration tick, Duration trafficEvery, PatrolSimulator patrols) {
        this.patrols = patrols;
        this.graph = Objects.requireNonNull(graph, "graph");
        this.traffic = Objects.requireNonNull(traffic, "traffic");
        this.roadConditions = Objects.requireNonNull(roadConditions, "roadConditions");
        this.trafficRecorder = Objects.requireNonNull(trafficRecorder, "trafficRecorder");
        this.routeMonitor = Objects.requireNonNull(routeMonitor, "routeMonitor");
        this.movement = Objects.requireNonNull(movement, "movement");
        this.tracking = Objects.requireNonNull(tracking, "tracking");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tick = requirePositive(tick, "tick");
        this.trafficEvery = requirePositive(trafficEvery, "trafficEvery");
        this.lastTraffic = clock.instant();
    }

    // ------------------------------------------------------------------ start and stop

    /** Starts the cycle; starting one that runs does nothing (docs/api.md 10.2). */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        movement.resume();
        if (patrols != null) {
            patrols.resume();
        }
        startedAt = LocalDateTime.now(clock);
        scheduler = Executors.newSingleThreadScheduledExecutor(work -> {
            Thread thread = new Thread(work, "rutavida-simulation");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::tick, 0, tick.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Stops the cycle; the units stay where they are until it starts again. */
    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        scheduler = null;
        startedAt = null;
    }

    public synchronized boolean isRunning() {
        return scheduler != null;
    }

    /** When it was started; null while stopped. */
    public synchronized LocalDateTime startedAt() {
        return startedAt;
    }

    public long tickSeconds() {
        return tick.toSeconds();
    }

    /** Where the positions and the traffic come from: always the simulation in phase 1. */
    public TrafficSource source() {
        return traffic.source();
    }

    /**
     * For the demo (docs/api.md 10.3): sets the traffic level of a segment. It is applied,
     * saved and checked against the active routes in the next cycle.
     */
    public synchronized void forceCongestion(long segmentId, TrafficLevel level) {
        Objects.requireNonNull(level, "level");
        int edge = graph.edgeIndexOf(segmentId);
        if (edge < 0) {
            throw new NotFoundException("Tramo", segmentId);
        }
        traffic.forceCongestion(edge, TrafficRecorder.congestionOf(level));
        trafficForced = true;
    }

    // ------------------------------------------------------------------ the cycle

    /** One cycle. The scheduler calls it every {@code tick}; the tests call it directly. */
    public synchronized void tick() {
        Set<Long> changed = new HashSet<>();
        step("tráfico", () -> changed.addAll(updateTraffic()));
        step("incidentes", () -> changed.addAll(roadConditions.syncIncidents()));
        step("recálculo", () -> routeMonitor.checkActiveRoutes(changed));
        step("misiones", this::followMissions);
        step("posiciones", tracking::refreshPositions);
        step("llegadas", this::markArrivals);
        if (patrols != null) {
            step("patrullas", patrols::tick);
        }
    }

    /** Runs one step and logs its error, so the next steps and cycles still run. */
    private void step(String name, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Simulation step failed: " + name, e);
        }
    }

    private Set<Long> updateTraffic() {
        Instant now = clock.instant();
        if (!trafficForced && Duration.between(lastTraffic, now).compareTo(trafficEvery) < 0) {
            return Set.of();
        }
        trafficForced = false;
        lastTraffic = now;
        List<EdgeUpdate> updates = traffic.poll();
        Set<Long> changed = roadConditions.applyTraffic(updates);
        trafficRecorder.record(updates, traffic.source());
        return changed;
    }

    /**
     * Each unit follows the ACTIVA route of its mission. A new mission leaves (EN_RUTA); a
     * route that changed (recalculated) replaces the old one; a mission that ended stops.
     */
    private void followMissions() {
        Set<Long> moving = new HashSet<>();
        for (ActiveMission mission : dispatch.activeMissions()) {
            long vehicleId = mission.assignment().getVehicleId();
            moving.add(vehicleId);
            missions.put(vehicleId, mission.assignment());
            if (Objects.equals(following.get(vehicleId), mission.route().getId())) {
                continue;
            }
            step("misión " + mission.assignment().getId(), () -> follow(mission));
        }
        for (Long vehicleId : Set.copyOf(following.keySet())) {
            if (!moving.contains(vehicleId)) {
                movement.stopFollowing(vehicleId);
                following.remove(vehicleId);
                missions.remove(vehicleId);
                arrived.remove(vehicleId);
            }
        }
    }

    /** The unit starts its new route; a mission that was ASIGNADA leaves (EN_RUTA). */
    private void follow(ActiveMission mission) {
        long vehicleId = mission.assignment().getVehicleId();
        Route route = mission.route();
        movement.follow(vehicleId, route, mission.vehicle());
        following.put(vehicleId, route.getId());
        arrived.remove(vehicleId);
        if (mission.assignment().getStatus() == AssignmentStatus.ASSIGNED) {
            missions.put(vehicleId, dispatch.advance(mission.assignment().getId(), AssignmentStatus.EN_ROUTE));
        }
    }

    /** A unit that reached the end of its route to the scene is EN_SITIO (docs/api.md 6.6). */
    private void markArrivals() {
        for (Long vehicleId : Set.copyOf(following.keySet())) {
            EmergencyAssignment mission = missions.get(vehicleId);
            if (movement.isFollowing(vehicleId) || arrived.contains(vehicleId) || mission == null) {
                continue;
            }
            arrived.add(vehicleId);
            if (mission.getStatus() == AssignmentStatus.EN_ROUTE) {
                step("llegada " + mission.getId(), () -> dispatch.advance(mission.getId(), AssignmentStatus.ON_SCENE));
            }
            // At a hospital (TRASLADANDO) the coordinator ends the mission (K7b).
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
