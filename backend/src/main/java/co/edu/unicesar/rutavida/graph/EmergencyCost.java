package co.edu.unicesar.rutavida.graph;

/** Rule RN-07: travel time with congestion and traffic light wait, adjusted to the vehicle. */
public class EmergencyCost implements CostFunction {

    @Override
    public double seconds(Edge edge, EdgeState state, VehicleProfile vehicle) {
        if (state.closed()) {
            return Double.POSITIVE_INFINITY;
        }
        double speedKmh = Math.min(edge.freeFlowKmh(), vehicle.maxKmh());
        double effectiveCongestion = 1 + (state.congestion() - 1) * (1 - vehicle.congestionRelief());
        double travel = edge.lengthM() / (speedKmh / 3.6) * effectiveCongestion;
        double signal = state.signalDelaySec() * (1 - vehicle.signalRelief());
        return (travel + signal) * state.incidentFactor();
    }
}