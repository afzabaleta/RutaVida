package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.TrafficReading;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.simulation.SimulationTestSupport.ManualClock;
import co.edu.unicesar.rutavida.traffic.EdgeUpdate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** TrafficRecorder on the streets of DispatchTestSupport (220 m, 40 km/h), without Oracle. */
class TrafficRecorderTest {

    private final RoadGraph graph = new DispatchTestSupport().graph;
    private final ManualClock clock = new ManualClock(CLOCK.instant());
    private final List<List<TrafficReading>> batches = new ArrayList<>();
    private final EmergencyTestSupport.CountingTransactions transactions = new EmergencyTestSupport.CountingTransactions();
    private final TrafficRecorder recorder = new TrafficRecorder(graph, batches::add, transactions, clock);

    private int record(int edge, double congestion) {
        return recorder.record(List.of(new EdgeUpdate(edge, congestion, 0)), TrafficSource.SIMULATION);
    }

    @Test
    void theLevelOfACongestion() {
        assertEquals(TrafficLevel.FLUID, TrafficRecorder.levelOf(1.0));
        assertEquals(TrafficLevel.FLUID, TrafficRecorder.levelOf(1.19));
        assertEquals(TrafficLevel.MODERATE, TrafficRecorder.levelOf(1.2));
        assertEquals(TrafficLevel.CONGESTED, TrafficRecorder.levelOf(1.5));
        for (TrafficLevel level : TrafficLevel.values()) {
            assertEquals(level, TrafficRecorder.levelOf(TrafficRecorder.congestionOf(level)), "a forced level stays");
        }
    }

    @Test
    void onlyTheChangesOfLevelAreSaved() {
        assertEquals(1, recorder.record(List.of(new EdgeUpdate(0, 1.0, 0), new EdgeUpdate(1, 2.0, 12)),
                TrafficSource.SIMULATION), "a street that was always FLUIDO is not written");
        TrafficReading congested = batches.get(0).get(0);
        assertEquals(graph.edge(1).dbId(), congested.getSegmentId());
        assertEquals(TrafficLevel.CONGESTED, congested.getLevel());
        assertEquals(2.0, congested.getCongestionFactor(), 1e-9);
        assertEquals(20.0, congested.getAverageSpeedKmh(), 1e-9, "40 km/h twice as slow");
        assertEquals(TrafficSource.SIMULATION, congested.getSource());

        assertEquals(0, record(1, 2.4), "still CONGESTIONADO");
        assertEquals(1, record(1, 1.3), "now MODERADO");
        assertEquals(1, record(1, 1.0), "back to FLUIDO is a change too");
        assertEquals(3, batches.size());
        assertEquals(3, transactions.calls, "one transaction per call that writes");
    }

    @Test
    void aLevelThatStaysIsSavedAgainAfterTenMinutes() {
        assertEquals(1, record(2, 2.0));
        clock.advance(9 * 60);
        assertEquals(0, record(2, 2.2));
        clock.advance(60);
        assertEquals(1, record(2, 2.2), "so the dashboard, which looks 15 min back, still counts it");
    }
}
