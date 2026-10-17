package co.edu.unicesar.rutavida.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceOnlyCostTest {

    private final Edge edge = new Edge(0, 100, 0, 1, 550, 30, "Test");
    private final DistanceOnlyCost cost = new DistanceOnlyCost();
    private final VehicleProfile ambulance = VehicleProfile.AMBULANCE;

    @Test
    void ignoresTraffic() {
        assertEquals(22.0, cost.seconds(edge, EdgeState.free(), ambulance), 0.001);
        assertEquals(22.0, cost.seconds(edge, new EdgeState(3.0, false, 20, 1.5, 0), ambulance), 0.001);
    }

    @Test
    void closedEdgeIsInfinite() {
        assertEquals(Double.POSITIVE_INFINITY,
                cost.seconds(edge, new EdgeState(1.0, true, 0, 1.0, 0), ambulance));
    }
}