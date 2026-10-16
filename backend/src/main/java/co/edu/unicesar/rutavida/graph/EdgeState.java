package co.edu.unicesar.rutavida.graph;
/**
 * Current condition of one edge. Immutable: every update replaces the whole object,
 * so a route calculation never reads half-updated values.
 *
 * @param congestion     1.0 = free flow, 2.0 = twice as slow (never below 1.0)
 * @param closed         true = the router must avoid the edge
 * @param signalDelaySec expected wait at the traffic light at the end of the edge
 * @param incidentFactor 1.0 = no incident; greater than 1.0 = penalized by an active incident
 * @param updatedAtMs    when the state was produced (epoch milliseconds)
 */
public record EdgeState(double congestion, boolean closed, double signalDelaySec,
                        double incidentFactor, long updatedAtMs) {

    private static final EdgeState FREE = new EdgeState(1.0, false, 0.0, 1.0, 0L);

    public EdgeState {
        if (!(congestion >= 1.0)) {
            throw new IllegalArgumentException("congestion must be >= 1.0");
        }
        if (!(signalDelaySec >= 0.0)) {
            throw new IllegalArgumentException("signalDelaySec must be >= 0");
        }
        if (!(incidentFactor >= 1.0)) {
            throw new IllegalArgumentException("incidentFactor must be >= 1.0");
        }
    }

    /** Free flow, open, no traffic light delay and no incident. */
    public static EdgeState free() {
        return FREE;
    }
}
