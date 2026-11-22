package co.edu.unicesar.rutavida.traffic;

import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // ---------------------------------------------------------- How much of the city is congested

    private static final int MAIN = 200;
    private static final int LOCAL = 1800;

    /** A city like Valledupar: 10 % main roads (50 km/h) and 90 % local streets (30 km/h). */
    private static RoadGraph city() {
        List<Node> nodes = List.of(new Node(0, 1, 10.46, -73.26), new Node(1, 2, 10.47, -73.25));
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < MAIN + LOCAL; i++) {
            edges.add(new Edge(i, 1000 + i, i % 2, (i + 1) % 2, 120, i < MAIN ? 50 : 30, "Calle " + i));
        }
        return new RoadGraph(nodes, edges);
    }

    /** The last factor of each edge after an hour of polls (240 polls, one every 15 s). */
    private static Map<Integer, Double> afterAnHour(Clock clock) {
        SimulatedTrafficProvider provider = new SimulatedTrafficProvider(city(), new Random(2026), clock);
        Map<Integer, Double> last = new HashMap<>();
        for (int i = 0; i < 240; i++) {
            for (EdgeUpdate update : provider.poll()) {
                last.put(update.edgeIndex(), update.congestion());
            }
        }
        return last;
    }

    private static double share(Map<Integer, Double> last, int from, int to, double minimum) {
        long count = last.entrySet().stream()
                .filter(e -> e.getKey() >= from && e.getKey() < to && e.getValue() >= minimum)
                .count();
        return (double) count / (to - from);
    }

    @Test
    void mostOfTheCityFlowsEvenAtPeakHour() {
        Map<Integer, Double> peak = afterAnHour(PEAK);
        Map<Integer, Double> offPeak = afterAnHour(OFF_PEAK);

        // With traffic = MODERADO (1.2) or more; congested = 1.5 or more (TrafficRecorder)
        double peakWithTraffic = share(peak, 0, MAIN + LOCAL, 1.2);
        double offPeakWithTraffic = share(offPeak, 0, MAIN + LOCAL, 1.2);
        assertTrue(peakWithTraffic < 0.15, "peak: " + peakWithTraffic);
        assertTrue(offPeakWithTraffic < 0.07, "off-peak: " + offPeakWithTraffic);
        assertTrue(peakWithTraffic > offPeakWithTraffic, peakWithTraffic + " vs " + offPeakWithTraffic);
    }

    @Test
    void theJamsAreOnTheMainRoads() {
        Map<Integer, Double> peak = afterAnHour(PEAK);

        double mainCongested = share(peak, 0, MAIN, 1.5);
        double localCongested = share(peak, MAIN, MAIN + LOCAL, 1.5);
        assertTrue(mainCongested > 0.15, "main roads congested: " + mainCongested);
        assertTrue(localCongested < 0.03, "local streets congested: " + localCongested);
    }
}
