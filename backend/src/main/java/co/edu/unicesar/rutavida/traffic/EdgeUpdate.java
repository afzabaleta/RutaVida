package co.edu.unicesar.rutavida.traffic;

/**
 * Traffic change for one edge. It never carries "closed": closures come only from incidents (RN-06).
 *
 * @param edgeIndex      edge index in RoadGraph
 * @param congestion     1.0 = free flow (never below 1.0)
 * @param signalDelaySec expected traffic light wait in seconds
 */
public record EdgeUpdate(int edgeIndex, double congestion, double signalDelaySec) {

    public EdgeUpdate {
        if (edgeIndex < 0) {
            throw new IllegalArgumentException("edgeIndex must be >= 0");
        }
        if (!(congestion >= 1.0)) {
            throw new IllegalArgumentException("congestion must be >= 1.0");
        }
        if (!(signalDelaySec >= 0.0)) {
            throw new IllegalArgumentException("signalDelaySec must be >= 0");
        }
    }
}