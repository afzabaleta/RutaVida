package co.edu.unicesar.rutavida.traffic;

import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedTrafficProviderTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Clock PEAK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), BOGOTA);
    private static final Clock OFF_PEAK = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), BOGOTA);

    private SimulatedTrafficProvider provider(RoadGraph graph, long seed, Clock clock) {
        return new SimulatedTrafficProvider(graph, new Random(seed), clock);
    }

    private double averageCongestion(SimulatedTrafficProvider provider, int polls) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < polls; i++) {
            for (EdgeUpdate update : provider.poll()) {
                sum += update.congestion();
                count++;
            }
        }
        return sum / count;
    }

    @Test
    void sameSeedSameResult() {
        RoadGraph graph = SampleGraph.sixNodes();
        SimulatedTrafficProvider a = provider(graph, 42, PEAK);
        SimulatedTrafficProvider b = provider(graph, 42, PEAK);
        for (int i = 0; i < 5; i++) {
            assertEquals(a.poll(), b.poll());
        }
    }

    @Test
    void valuesStayInRange() {
        SimulatedTrafficProvider provider = provider(SampleGraph.sixNodes(), 7, PEAK);
        for (int i = 0; i < 20; i++) {
            for (EdgeUpdate update : provider.poll()) {
                assertTrue(update.congestion() >= 1.0 && update.congestion() <= 3.0);
                assertTrue(update.signalDelaySec() >= 0 && update.signalDelaySec() <= 45.0);
            }
        }
    }

    @Test
    void peakHourIsMoreCongested() {
        RoadGraph graph = SampleGraph.sixNodes();
        double peak = averageCongestion(provider(graph, 42, PEAK), 50);
        double offPeak = averageCongestion(provider(graph, 42, OFF_PEAK), 50);
        assertTrue(peak > offPeak);
    }

    @Test
    void forcedCongestionAppearsOnlyInTheNextPoll() {
        SimulatedTrafficProvider provider = provider(SampleGraph.sixNodes(), 42, OFF_PEAK);
        provider.forceCongestion(0, 2.5);
        List<EdgeUpdate> first = provider.poll();
        assertEquals(new EdgeUpdate(0, 2.5, 0), first.get(0));
        List<EdgeUpdate> second = provider.poll();
        assertTrue(second.stream().noneMatch(u -> u.congestion() == 2.5));
    }

    @Test
    void forcedCongestionIsNotOverwrittenInTheSamePoll() {
        RoadGraph graph = SampleGraph.sixNodes();
        SimulatedTrafficProvider provider = provider(graph, 42, PEAK);
        for (int i = 0; i < graph.edgeCount(); i++) {
            provider.forceCongestion(i, 2.5);
        }
        List<EdgeUpdate> updates = provider.poll();
        assertEquals(graph.edgeCount(), updates.size());
        for (EdgeUpdate update : updates) {
            assertEquals(2.5, update.congestion(), 0.0001);
            assertEquals(0.0, update.signalDelaySec(), 0.0001);
        }
    }

    @Test
    void doesNotTouchTheGraph() {
        RoadGraph graph = SampleGraph.sixNodes();
        SimulatedTrafficProvider provider = provider(graph, 42, PEAK);
        for (int i = 0; i < 10; i++) {
            provider.poll();
        }
        for (int i = 0; i < graph.edgeCount(); i++) {
            assertEquals(EdgeState.free(), graph.state(i));
        }
    }

    @Test
    void sourceIsSimulation() {
        assertEquals(TrafficSource.SIMULATION, provider(SampleGraph.sixNodes(), 1, PEAK).source());
    }
}