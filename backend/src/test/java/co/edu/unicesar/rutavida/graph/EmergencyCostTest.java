package co.edu.unicesar.rutavida.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmergencyCostTest {

    private final Edge edge = new Edge(0, 100, 0, 1, 550, 30, "Test");
    private final EmergencyCost cost = new EmergencyCost();
    private final VehicleProfile ambulance = VehicleProfile.AMBULANCE;

    @Test
    void freeFlow() {
        assertEquals(66.0, cost.seconds(edge, EdgeState.free(), ambulance), 0.001);
    }

    @Test
    void congestion() {
        assertEquals(151.8, cost.seconds(edge, new EdgeState(3.0, false, 0, 1.0, 0), ambulance), 0.001);
    }

    @Test
    void trafficLight() {
        assertEquals(74.0, cost.seconds(edge, new EdgeState(1.0, false, 20, 1.0, 0), ambulance), 0.001);
    }

    @Test
    void incidentFactor() {
        assertEquals(99.0, cost.seconds(edge, new EdgeState(1.0, false, 0, 1.5, 0), ambulance), 0.001);
    }

    @Test
    void closedEdgeIsInfinite() {
        assertEquals(Double.POSITIVE_INFINITY,
                cost.seconds(edge, new EdgeState(1.0, true, 0, 1.0, 0), ambulance));
    }
}