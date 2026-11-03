package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadNodeDAO;
import co.edu.unicesar.rutavida.dao.RoadSegmentDAO;
import co.edu.unicesar.rutavida.dao.TrafficLightDAO;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.RoadNode;
import co.edu.unicesar.rutavida.model.RoadSegment;
import co.edu.unicesar.rutavida.model.TrafficLight;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Builds the in-memory RoadGraph from the road network stored in Oracle (NODO_VIAL,
 * TRAMO_VIAL and SEMAFORO), so the route engine works on the real streets of the demo zone.
 *
 * <ul>
 *   <li>Only active nodes are used, and only segments whose two nodes are in the graph.</li>
 *   <li>Only the largest connected part of the network is kept: a small island (a street
 *       that OpenStreetMap does not connect to the rest) would make the nearest node of a
 *       point unreachable.</li>
 *   <li>A CERRADO segment stays in the graph but closed, so it can be reopened later;
 *       RESTRINGIDO segments are open.</li>
 *   <li>An active traffic light adds its expected wait to the segments that arrive at its
 *       node (see {@link #expectedWaitSeconds}).</li>
 * </ul>
 */
public class GraphLoader {

    private static final Logger LOG = Logger.getLogger(GraphLoader.class.getName());

    /** Assumption (not measured): cycle of a traffic light without tiempo_ciclo_seg. */
    static final int DEFAULT_CYCLE_SECONDS = 90;

    private final RoadNodeDAO nodeDao;
    private final RoadSegmentDAO segmentDao;
    private final TrafficLightDAO trafficLightDao;

    public GraphLoader(RoadNodeDAO nodeDao, RoadSegmentDAO segmentDao, TrafficLightDAO trafficLightDao) {
        this.nodeDao = Objects.requireNonNull(nodeDao, "nodeDao");
        this.segmentDao = Objects.requireNonNull(segmentDao, "segmentDao");
        this.trafficLightDao = Objects.requireNonNull(trafficLightDao, "trafficLightDao");
    }

    /**
     * Reads the network and builds the graph. Throws IllegalStateException when there is
     * no network to route on (run database/05_red_vial_valledupar.sql first).
     */
    public RoadGraph load() {
        Map<Long, RoadNode> activeNodes = new HashMap<>();
        for (RoadNode node : nodeDao.findAll()) {
            if (node.getStatus() == RecordStatus.ACTIVE) {
                activeNodes.put(node.getId(), node);
            }
        }
        List<RoadSegment> usable = new ArrayList<>();
        for (RoadSegment segment : segmentDao.findAll()) {
            if (activeNodes.containsKey(segment.getFromNodeId()) && activeNodes.containsKey(segment.getToNodeId())) {
                usable.add(segment);
            }
        }
        if (usable.isEmpty()) {
            throw new IllegalStateException(
                    "No hay red vial cargada en Oracle: ejecute database/05_red_vial_valledupar.sql");
        }

        Set<Long> kept = largestConnectedPart(usable);

        List<Node> nodes = new ArrayList<>();
        Map<Long, Integer> indexOf = new HashMap<>();
        activeNodes.values().stream()
                .filter(node -> kept.contains(node.getId()))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .forEach(node -> {
                    indexOf.put(node.getId(), nodes.size());
                    nodes.add(new Node(nodes.size(), node.getId(),
                            node.getLocation().latitude(), node.getLocation().longitude()));
                });

        List<Edge> edges = new ArrayList<>();
        List<RoadSegment> edgeSegments = new ArrayList<>();
        for (RoadSegment segment : usable) {
            Integer from = indexOf.get(segment.getFromNodeId());
            Integer to = indexOf.get(segment.getToNodeId());
            if (from == null || to == null) {
                continue;
            }
            edges.add(new Edge(edges.size(), segment.getId(), from, to,
                    segment.getLengthMeters(), segment.getMaxSpeedKmh(), segment.getRoadName()));
            edgeSegments.add(segment);
        }

        RoadGraph graph = new RoadGraph(nodes, edges);
        Map<Integer, Double> waitAtNode = signalWaits(indexOf);
        int closed = 0;
        for (Edge edge : edges) {
            boolean isClosed = edgeSegments.get(edge.index()).getStatus() == RoadSegmentStatus.CLOSED;
            double wait = waitAtNode.getOrDefault(edge.to(), 0.0);
            if (isClosed || wait > 0) {
                graph.update(edge.index(), new EdgeState(1.0, isClosed, wait, 1.0, 0L));
            }
            if (isClosed) {
                closed++;
            }
        }

        int closedEdges = closed;
        int leftOut = activeNodes.size() - nodes.size();
        LOG.info(() -> String.format("Grafo cargado: %d nodos, %d tramos (%d cerrados); %d nodos fuera de la red principal",
                graph.nodeCount(), graph.edgeCount(), closedEdges, leftOut));
        return graph;
    }

    /**
     * Expected wait for a vehicle that arrives at a random moment: the light is red about
     * half of the cycle, so the wait is (cycle / 2)² / (2 × cycle) = cycle / 8.
     * Assumption to calibrate, not a measurement (with 90 s: 11.25 s).
     */
    static double expectedWaitSeconds(Integer cycleSeconds) {
        int cycle = cycleSeconds == null ? DEFAULT_CYCLE_SECONDS : cycleSeconds;
        return cycle / 8.0;
    }

    /** Wait to add at each graph node that has a working traffic light (the longest one if several). */
    private Map<Integer, Double> signalWaits(Map<Long, Integer> indexOf) {
        Map<Integer, Double> waits = new HashMap<>();
        for (TrafficLight light : trafficLightDao.findAll()) {
            Integer node = indexOf.get(light.getNodeId());
            if (node == null || light.getStatus() != RecordStatus.ACTIVE || light.getCurrentState() == TrafficLightState.OUT_OF_SERVICE) {
                continue;
            }
            waits.merge(node, expectedWaitSeconds(light.getCycleSeconds()), Math::max);
        }
        return waits;
    }

    /**
     * Ids of the nodes of the biggest group connected by segments (direction ignored).
     * The nodes are visited in id order, so the result is always the same.
     */
    private static Set<Long> largestConnectedPart(List<RoadSegment> segments) {
        Map<Long, List<Long>> neighbours = new TreeMap<>();
        for (RoadSegment segment : segments) {
            neighbours.computeIfAbsent(segment.getFromNodeId(), key -> new ArrayList<>()).add(segment.getToNodeId());
            neighbours.computeIfAbsent(segment.getToNodeId(), key -> new ArrayList<>()).add(segment.getFromNodeId());
        }
        Set<Long> seen = new HashSet<>();
        Set<Long> best = Set.of();
        for (Long start : neighbours.keySet()) {
            if (seen.contains(start)) {
                continue;
            }
            Set<Long> part = new HashSet<>();
            Deque<Long> pending = new ArrayDeque<>(List.of(start));
            seen.add(start);
            while (!pending.isEmpty()) {
                Long node = pending.pop();
                part.add(node);
                for (Long next : neighbours.get(node)) {
                    if (seen.add(next)) {
                        pending.push(next);
                    }
                }
            }
            if (part.size() > best.size()) {
                best = part;
            }
        }
        return best;
    }
}