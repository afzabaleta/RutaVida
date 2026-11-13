package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.graph.AStarRouter;
import co.edu.unicesar.rutavida.graph.AlternativeRouteFinder;
import co.edu.unicesar.rutavida.graph.CostFunction;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Routes of a mission on the real road network (CU-08): the recommended route and up to
 * two alternatives with their explanation, the travel time used to suggest a unit, and
 * saving the routes of a mission in RUTA and RUTA_TRAMO.
 *
 * <p>The graph is loaded once (GraphLoader) and shared; this service only reads it.
 */
public class RouteService {

    /** A point farther than this from the nearest node is outside the loaded road network. */
    public static final double MAX_SNAP_METERS = 300;

    /** Rule RN-08: an alternative shares at most 80 % of its segments and takes at most 1.5 times longer. */
    static final double MAX_OVERLAP = 0.8;
    static final double MAX_SLOWDOWN = 1.5;
    static final int MAX_ROUTES = 3;

    private final RoadGraph graph;
    private final RouteDAO routeDao;
    private final TransactionManager transactions;
    private final Clock clock;
    private final CostFunction cost = new EmergencyCost();
    private final AStarRouter router = new AStarRouter();
    private final AlternativeRouteFinder finder;
    private final RouteExplanationBuilder explainer = new RouteExplanationBuilder();

    public RouteService(RoadGraph graph, RouteDAO routeDao, TransactionManager transactions, Clock clock) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.routeDao = Objects.requireNonNull(routeDao, "routeDao");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.finder = new AlternativeRouteFinder(router, cost, MAX_OVERLAP, MAX_SLOWDOWN);
    }

    /** True when the point is close enough to a node of the loaded road network. */
    public boolean isWithinNetwork(GeoPoint point) {
        ValidationUtil.requireLocation(point);
        int node = graph.nearestNode(point.latitude(), point.longitude());
        return graph.node(node).distanceMetersTo(point.latitude(), point.longitude()) <= MAX_SNAP_METERS;
    }

    /**
     * The recommended route (rank 1) and up to two alternatives, fastest first. Throws
     * BusinessRuleException if a point is outside the network or there is no way between them.
     */
    public List<RouteOption> calculateOptions(GeoPoint from, GeoPoint to, VehicleProfile vehicle) {
        ValidationUtil.requireNonNull(vehicle, "tipo de vehículo");
        int src = nodeFor(from, "origen");
        int dst = nodeFor(to, "destino");
        if (src == dst) {
            throw new BusinessRuleException("El origen y el destino quedan en el mismo punto de la red vial.");
        }
        List<RouteResult> found = finder.find(graph, src, dst, vehicle, MAX_ROUTES);
        if (found.isEmpty()) {
            throw new BusinessRuleException("No hay una ruta por la red vial entre el origen y el destino.");
        }
        List<RouteOption> options = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            options.add(explainer.toOption(i + 1, found.get(i), found.get(0), graph));
        }
        return options;
    }

    /**
     * Seconds of the fastest route with the current traffic, used to compare units
     * (CU-09). Empty if there is no way between the points; 0 if they share a node.
     */
    public OptionalDouble estimateSeconds(GeoPoint from, GeoPoint to, VehicleProfile vehicle) {
        ValidationUtil.requireNonNull(vehicle, "tipo de vehículo");
        int src = nodeFor(from, "origen");
        int dst = nodeFor(to, "destino");
        if (src == dst) {
            return OptionalDouble.of(0);
        }
        Optional<RouteResult> best = router.route(graph, src, dst, vehicle, cost, null, true);
        return best.isPresent() ? OptionalDouble.of(best.get().seconds()) : OptionalDouble.empty();
    }

    /**
     * Seconds the unit still needs on its route with the traffic and incidents of now,
     * counted from the segment of the route nearest to its position (CU-17). Empty when a
     * segment that is still ahead is closed or is no longer in the loaded network.
     */
    public OptionalDouble remainingSeconds(Route route, GeoPoint position, VehicleProfile vehicle) {
        ValidationUtil.requireNonNull(route, "ruta");
        ValidationUtil.requireLocation(position);
        ValidationUtil.requireNonNull(vehicle, "tipo de vehículo");
        List<RouteSegment> segments = route.getSegments();
        int start = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < segments.size(); i++) {
            int edge = graph.edgeIndexOf(segments.get(i).segmentId());
            if (edge < 0) {
                return OptionalDouble.empty();
            }
            double meters = graph.node(graph.edge(edge).from())
                    .distanceMetersTo(position.latitude(), position.longitude());
            if (meters < nearest) {
                nearest = meters;
                start = i;
            }
        }
        double seconds = 0;
        for (int i = start; i < segments.size(); i++) {
            int edge = graph.edgeIndexOf(segments.get(i).segmentId());
            double part = cost.seconds(graph.edge(edge), graph.state(edge), vehicle);
            if (Double.isInfinite(part)) {
                return OptionalDouble.empty();
            }
            seconds += part;
        }
        return OptionalDouble.of(seconds);
    }

    /** Where a saved route ends: the last node of its last segment (the emergency or the hospital). */
    public GeoPoint destinationOf(Route route) {
        ValidationUtil.requireNonNull(route, "ruta");
        List<RouteSegment> segments = route.getSegments();
        int edge = segments.isEmpty() ? -1 : graph.edgeIndexOf(segments.get(segments.size() - 1).segmentId());
        if (edge < 0) {
            throw new BusinessRuleException("La ruta usa un tramo que no está en la red vial cargada.");
        }
        Node end = graph.node(graph.edge(edge).to());
        return new GeoPoint(end.lat(), end.lon());
    }

    /**
     * Saves the routes of one mission in one transaction: the recommended route as ACTIVA
     * (the one the unit drives) and the alternatives as PROPUESTA. Called by the dispatch
     * when the unit leaves. Each segment keeps its estimated time, congestion and traffic
     * light at this moment.
     */
    public List<Route> saveForMission(long emergencyId, long vehicleId, Long hospitalId,
                                      VehicleProfile vehicle, List<RouteOption> options) {
        ValidationUtil.requirePositiveId(emergencyId, "emergencia");
        ValidationUtil.requirePositiveId(vehicleId, "vehículo");
        if (hospitalId != null) {
            ValidationUtil.requirePositiveId(hospitalId, "hospital");
        }
        ValidationUtil.requireNonNull(vehicle, "tipo de vehículo");
        if (options == null || options.isEmpty()) {
            throw new ValidationException("No hay rutas para guardar.");
        }
        if (options.get(0).rank() != 1) {
            throw new ValidationException("La primera ruta debe ser la recomendada.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return transactions.callInTransaction(() -> {
            List<Route> saved = new ArrayList<>();
            for (RouteOption option : options) {
                Route route = toRoute(emergencyId, vehicleId, hospitalId, vehicle, option, now);
                routeDao.insert(route);
                saved.add(route);
            }
            return saved;
        });
    }

    private Route toRoute(long emergencyId, long vehicleId, Long hospitalId, VehicleProfile vehicle,
                          RouteOption option, LocalDateTime now) {
        List<RouteSegment> segments = new ArrayList<>();
        double totalSeconds = 0;
        List<Long> ids = option.segmentIds();
        for (int i = 0; i < ids.size(); i++) {
            int edge = graph.edgeIndexOf(ids.get(i));
            if (edge < 0) {
                throw new BusinessRuleException("La ruta usa un tramo que no está en la red vial cargada.");
            }
            EdgeState state = graph.state(edge);
            double seconds = cost.seconds(graph.edge(edge), state, vehicle);
            totalSeconds += seconds;
            segments.add(new RouteSegment(i + 1, ids.get(i), seconds, state.congestion(),
                    state.signalDelaySec() > 0 ? 1 : 0));
        }
        boolean recommended = option.rank() == 1;
        return new Route(0, emergencyId, vehicleId, hospitalId, option.type(), option.distanceKm(),
                option.estimatedMinutes(), totalSeconds, now, recommended,
                recommended ? RouteStatus.ACTIVE : RouteStatus.PROPOSED, segments);
    }

    /** Index of the graph node for a point, or BusinessRuleException if it is outside the network. */
    private int nodeFor(GeoPoint point, String label) {
        ValidationUtil.requireLocation(point);
        int node = graph.nearestNode(point.latitude(), point.longitude());
        if (graph.node(node).distanceMetersTo(point.latitude(), point.longitude()) > MAX_SNAP_METERS) {
            throw new BusinessRuleException("El punto de " + label + " está fuera de la red vial cargada.");
        }
        return node;
    }
}