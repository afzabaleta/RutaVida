package co.edu.unicesar.rutavida.traffic;

import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Simulated traffic (phase 1). Never closes edges and never modifies the graph. */
public class SimulatedTrafficProvider implements TrafficProvider {

    private static final double SAMPLE_SHARE = 0.10;
    private static final double MAX_SIGNAL_DELAY_SEC = 45.0;

    private final int edgeCount;
    private final Random random;
    private final Clock clock;
    private final Map<Integer, Double> forced = new LinkedHashMap<>();

    public SimulatedTrafficProvider(RoadGraph graph, Random random, Clock clock) {
        if (graph == null || random == null || clock == null) {
            throw new IllegalArgumentException("graph, random and clock are required");
        }
        this.edgeCount = graph.edgeCount();
        this.random = random;
        this.clock = clock;
    }

    @Override
    public synchronized List<EdgeUpdate> poll() {
        List<EdgeUpdate> updates = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : forced.entrySet()) {
            updates.add(new EdgeUpdate(entry.getKey(), entry.getValue(), 0));
        }
        Set<Integer> forcedNow = new HashSet<>(forced.keySet());
        forced.clear();

        int count = Math.max(1, (int) Math.round(edgeCount * SAMPLE_SHARE));
        boolean peak = isPeakHour();
        for (int i = 0; i < count; i++) {
            int edge = random.nextInt(edgeCount);
            double congestion = peak ? 1.5 + random.nextDouble() * 1.5 : 1.0 + random.nextDouble() * 0.8;
            double signal = random.nextDouble() * MAX_SIGNAL_DELAY_SEC;
            if (!forcedNow.contains(edge)) {
                updates.add(new EdgeUpdate(edge, congestion, signal));
            }
        }
        return updates;
    }

    /** For the demo: congest this edge now; it appears in the next poll(). */
    public synchronized void forceCongestion(int edgeIndex, double congestion) {
        if (edgeIndex < 0 || edgeIndex >= edgeCount) {
            throw new IllegalArgumentException("edgeIndex out of range: " + edgeIndex);
        }
        if (!(congestion >= 1.0)) {
            throw new IllegalArgumentException("congestion must be >= 1.0");
        }
        forced.put(edgeIndex, congestion);
    }

    @Override
    public TrafficSource source() {
        return TrafficSource.SIMULATION;
    }

    /** Peak hours are assumptions to calibrate (6:30-8:30, 11:30-13:30, 17:00-19:00), not measured data. */
    private boolean isPeakHour() {
        LocalTime now = LocalTime.now(clock);
        return between(now, 6, 30, 8, 30) || between(now, 11, 30, 13, 30) || between(now, 17, 0, 19, 0);
    }

    private static boolean between(LocalTime t, int h1, int m1, int h2, int m2) {
        return !t.isBefore(LocalTime.of(h1, m1)) && t.isBefore(LocalTime.of(h2, m2));
    }
}