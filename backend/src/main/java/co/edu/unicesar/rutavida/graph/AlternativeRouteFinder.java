package co.edu.unicesar.rutavida.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Alternative routes by the penalty method (rule RN-08). Never modifies the graph. */
public class AlternativeRouteFinder {

    private static final double PENALTY_STEP = 1.4;

    private final AStarRouter router;
    private final CostFunction cost;
    private final double maxOverlap;
    private final double maxSlowdown;

    public AlternativeRouteFinder(AStarRouter router, CostFunction cost, double maxOverlap, double maxSlowdown) {
        if (router == null || cost == null) {
            throw new IllegalArgumentException("router and cost are required");
        }
        if (!(maxOverlap > 0 && maxOverlap <= 1) || !(maxSlowdown >= 1)) {
            throw new IllegalArgumentException("invalid maxOverlap or maxSlowdown");
        }
        this.router = router;
        this.cost = cost;
        this.maxOverlap = maxOverlap;
        this.maxSlowdown = maxSlowdown;
    }

    public List<RouteResult> find(RoadGraph graph, int src, int dst, VehicleProfile vehicle, int maxRoutes) {
        List<RouteResult> chosen = new ArrayList<>();
        if (maxRoutes < 1) {
            return chosen;
        }
        Optional<RouteResult> best = router.route(graph, src, dst, vehicle, cost, null, true);
        if (best.isEmpty()) {
            return chosen;
        }
        RouteResult first = withRealSeconds(graph, best.get(), vehicle);
        chosen.add(first);

        Map<Integer, Double> penalty = new HashMap<>();
        penalize(penalty, first);

        int attempts = maxRoutes * 3;
        for (int i = 0; i < attempts && chosen.size() < maxRoutes; i++) {
            Optional<RouteResult> found = router.route(graph, src, dst, vehicle, cost, penalty, true);
            if (found.isEmpty()) {
                break;
            }
            RouteResult candidate = withRealSeconds(graph, found.get(), vehicle);
            penalize(penalty, candidate);
            if (isAcceptable(candidate, chosen, first)) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }

    private boolean isAcceptable(RouteResult candidate, List<RouteResult> chosen, RouteResult best) {
        for (RouteResult other : chosen) {
            if (other.edgeIds().equals(candidate.edgeIds())) {
                return false;
            }
            if (overlap(candidate, other) > maxOverlap) {
                return false;
            }
        }
        return candidate.seconds() <= best.seconds() * maxSlowdown;
    }

    private static double overlap(RouteResult candidate, RouteResult other) {
        if (candidate.edgeIds().isEmpty()) {
            return 1.0;
        }
        Set<Integer> otherEdges = new HashSet<>(other.edgeIds());
        long common = candidate.edgeIds().stream().filter(otherEdges::contains).count();
        return (double) common / candidate.edgeIds().size();
    }

    private static void penalize(Map<Integer, Double> penalty, RouteResult route) {
        for (int edgeId : route.edgeIds()) {
            penalty.merge(edgeId, PENALTY_STEP, (old, step) -> old * step);
        }
    }

    private RouteResult withRealSeconds(RoadGraph graph, RouteResult route, VehicleProfile vehicle) {
        double seconds = 0;
        for (int edgeId : route.edgeIds()) {
            seconds += cost.seconds(graph.edge(edgeId), graph.state(edgeId), vehicle);
        }
        return new RouteResult(route.edgeIds(), seconds, route.meters(), route.exploredNodes());
    }
}