package co.edu.unicesar.rutavida.graph;

public interface CostFunction {

    double seconds(Edge edge, EdgeState state, VehicleProfile vehicle);
}
