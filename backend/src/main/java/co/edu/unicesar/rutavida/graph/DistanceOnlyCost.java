package co.edu.unicesar.rutavida.graph;

/** Ignores traffic, lights and incidents. Used to show the shortest route is not always the fastest. */
public class DistanceOnlyCost implements CostFunction {

    @Override
    public double seconds(Edge edge, EdgeState state, VehicleProfile vehicle) {
        if (state.closed()) {
            return Double.POSITIVE_INFINITY;
        }
        return edge.lengthM() / (vehicle.maxKmh() / 3.6);
    }
}
