package co.edu.unicesar.rutavida.graph;

import java.util.List;

/**
 * Result of a route calculation.
 *
 * @param edgeIds       edge indexes in travel order
 * @param seconds       total cost in seconds
 * @param meters        total length in meters
 * @param exploredNodes nodes taken out of the queue (metric for A* vs Dijkstra)
 */
public record RouteResult(List<Integer> edgeIds, double seconds, double meters, int exploredNodes) {

    public RouteResult {
        edgeIds = List.copyOf(edgeIds);
    }
}