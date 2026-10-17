package co.edu.unicesar.rutavida.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Road network in memory. The topology (nodes and edges) never changes after construction;
 * the state of each edge is replaced as a whole with {@link #update}. Reads need no locks.
 */
public class RoadGraph {

    private final Node[] nodes;
    private final Edge[] edges;
    private final List<Edge>[] outgoing;
    private final AtomicReferenceArray<EdgeState> states;
    private final Map<Long, Integer> nodeByDbId = new HashMap<>();
    private final Map<Long, Integer> edgeByDbId = new HashMap<>();
    private volatile long version;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RoadGraph(List<Node> nodeList, List<Edge> edgeList) {
        if (nodeList == null || nodeList.isEmpty() || edgeList == null) {
            throw new IllegalArgumentException("the graph needs at least one node and an edge list");
        }
        nodes = nodeList.toArray(Node[]::new);
        edges = edgeList.toArray(Edge[]::new);
        outgoing = new List[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].index() != i) {
                throw new IllegalArgumentException("node at position " + i + " has index " + nodes[i].index());
            }
            outgoing[i] = new ArrayList<>();
            nodeByDbId.put(nodes[i].dbId(), i);
        }
        for (int i = 0; i < edges.length; i++) {
            Edge edge = edges[i];
            if (edge.index() != i) {
                throw new IllegalArgumentException("edge at position " + i + " has index " + edge.index());
            }
            if (edge.from() >= nodes.length || edge.to() >= nodes.length) {
                throw new IllegalArgumentException("edge " + i + " points to a node that does not exist");
            }
            outgoing[edge.from()].add(edge);
            edgeByDbId.put(edge.dbId(), i);
        }
        for (int i = 0; i < nodes.length; i++) {
            outgoing[i] = Collections.unmodifiableList(outgoing[i]);
        }

        states = new AtomicReferenceArray<>(edges.length);
        for (int i = 0; i < edges.length; i++) {
            states.set(i, EdgeState.free());
        }
    }

    public int nodeCount() {
        return nodes.length;
    }

    public int edgeCount() {
        return edges.length;
    }

    public List<Edge> outgoing(int nodeIndex) {
        return outgoing[nodeIndex];
    }

    public Node node(int index) {
        return nodes[index];
    }

    public Edge edge(int index) {
        return edges[index];
    }

    public EdgeState state(int edgeIndex) {
        return states.get(edgeIndex);
    }

    /** Replaces the state of one edge. Only RoadConditionService should call it. */
    public synchronized void update(int edgeIndex, EdgeState state) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        states.set(edgeIndex, state);
        version++;
    }

    /** Index of the node with that Oracle id, or -1 if it is not in the graph. */
    public int nodeIndexOf(long dbId) {
        return nodeByDbId.getOrDefault(dbId, -1);
    }

    /** Index of the edge with that Oracle id, or -1 if it is not in the graph. */
    public int edgeIndexOf(long dbId) {
        return edgeByDbId.getOrDefault(dbId, -1);
    }

    /** Node closest to a coordinate (linear search: enough for a city-sized graph). */
    public int nearestNode(double lat, double lon) {
        int best = 0;
        double bestMeters = Double.POSITIVE_INFINITY;
        for (Node node : nodes) {
            double meters = node.distanceMetersTo(lat, lon);
            if (meters < bestMeters) {
                bestMeters = meters;
                best = node.index();
            }
        }
        return best;
    }

    /** Increases on every update; lets a cached route know the graph changed. */
    public long version() {
        return version;
    }
}
