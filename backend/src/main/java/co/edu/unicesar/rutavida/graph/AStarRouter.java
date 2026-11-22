package co.edu.unicesar.rutavida.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/** A* shortest path by cost in seconds. With useHeuristic = false it behaves as Dijkstra. */
public class AStarRouter {

    private record QueueEntry(int node, double priority) {
    }

    public Optional<RouteResult> route(RoadGraph graph, int src, int dst, VehicleProfile vehicle,
                                       CostFunction cost, Map<Integer, Double> penalty, boolean useHeuristic) {
        Map<Integer, Double> penalties = penalty == null ? Map.of() : penalty;
        int n = graph.nodeCount();

        if (src == dst) {
            return Optional.of(new RouteResult(List.of(), 0, 0, 0));
        }

        double[] g = new double[n];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] previousEdge = new int[n];
        Arrays.fill(previousEdge, -1);
        boolean[] closedSet = new boolean[n];

        double metersPerSecond = vehicle.maxKmh() / 3.6;
        Node target = graph.node(dst);

        PriorityQueue<QueueEntry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.priority(), b.priority()));
        g[src] = 0;
        queue.add(new QueueEntry(src, heuristic(graph.node(src), target, metersPerSecond, useHeuristic)));

        int explored = 0;
        while (!queue.isEmpty()) {
            int current = queue.poll().node();
            if (closedSet[current]) {
                continue;
            }
            closedSet[current] = true;
            explored++;
            if (current == dst) {
                return Optional.of(buildResult(graph, previousEdge, src, dst, g[dst], explored));
            }
            for (Edge edge : graph.outgoing(current)) {
                double base = cost.seconds(edge, graph.state(edge.index()), vehicle);
                if (Double.isInfinite(base)) {
                    continue;
                }
                double factor = Math.max(1.0, penalties.getOrDefault(edge.index(), 1.0));
                double candidate = g[current] + base * factor;
                if (candidate < g[edge.to()]) {
                    g[edge.to()] = candidate;
                    previousEdge[edge.to()] = edge.index();
                    double h = heuristic(graph.node(edge.to()), target, metersPerSecond, useHeuristic);
                    queue.add(new QueueEntry(edge.to(), candidate + h));
                }
            }
        }
        return Optional.empty();
    }

    private static double heuristic(Node from, Node target, double metersPerSecond, boolean useHeuristic) {
        return useHeuristic ? from.distanceMetersTo(target) / metersPerSecond : 0;
    }

    private static RouteResult buildResult(RoadGraph graph, int[] previousEdge, int src, int dst,
                                           double seconds, int explored) {
        List<Integer> path = new ArrayList<>();
        double meters = 0;
        int node = dst;
        while (node != src) {
            Edge edge = graph.edge(previousEdge[node]);
            path.add(edge.index());
            meters += edge.lengthM();
            node = edge.from();
        }
        Collections.reverse(path);
        return new RouteResult(path, seconds, meters, explored);
    }
}
