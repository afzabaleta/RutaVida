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

/**
 * Simulated traffic (phase 1). Never closes edges and never modifies the graph.
 *
 * <p>Each poll gives a new level to a few random edges ({@link #SAMPLE_SHARE}); an edge keeps
 * its level until it is drawn again. Most of the city flows: the jams are mostly on the main
 * roads (free-flow speed of {@link #MAIN_ROAD_FROM_KMH} or more, the avenues of the OSM
 * import) and more frequent at peak hours. Without this, every drawn edge got 1.0 to 3.0 and
 * after a few minutes the whole map was congested, so no route could avoid the traffic.
 *
 * <p>The shares are assumptions to calibrate with real data, not measurements. With the
 * Valledupar network (about 2 100 main and 20 000 local edges) they leave about 4 % of the
 * edges with traffic off-peak and about 9 % at peak.
 */
public class SimulatedTrafficProvider implements TrafficProvider {

    /** Share of the edges that get a new level in each poll (the engine polls every 15 s). */
    static final double SAMPLE_SHARE = 0.03;
    /** Free-flow speed from which an edge is a main road (local streets are 30 km/h). */
    static final double MAIN_ROAD_FROM_KMH = 40;

    /** Chance of each level for a drawn edge: {congested, moderate}; the rest flows. */
    private static final double[] MAIN_PEAK = {0.30, 0.30};
    private static final double[] MAIN_OFF_PEAK = {0.08, 0.17};
    private static final double[] LOCAL_PEAK = {0.01, 0.03};
    private static final double[] LOCAL_OFF_PEAK = {0.003, 0.012};

    private static final double MAX_SIGNAL_DELAY_SEC = 45.0;

    private final int edgeCount;
    private final boolean[] mainRoad;
    private final Random random;
    private final Clock clock;
    private final Map<Integer, Double> forced = new LinkedHashMap<>();

    public SimulatedTrafficProvider(RoadGraph graph, Random random, Clock clock) {
        if (graph == null || random == null || clock == null) {
            throw new IllegalArgumentException("graph, random and clock are required");
        }
        this.edgeCount = graph.edgeCount();
        this.mainRoad = new boolean[edgeCount];
        for (int i = 0; i < edgeCount; i++) {
            mainRoad[i] = graph.edge(i).freeFlowKmh() >= MAIN_ROAD_FROM_KMH;
        }
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
            double congestion = congestion(mainRoad[edge], peak);
            double signal = random.nextDouble() * MAX_SIGNAL_DELAY_SEC;
            if (!forcedNow.contains(edge)) {
                updates.add(new EdgeUpdate(edge, congestion, signal));
            }
        }
        return updates;
    }

    /**
     * A congestion factor for one drawn edge, between 1.0 and 3.0. The ranges match the levels
     * of TrafficRecorder: under 1.2 flows, 1.2 to 1.5 is moderate, 1.5 or more is congested.
     */
    private double congestion(boolean main, boolean peak) {
        double[] chances = main ? (peak ? MAIN_PEAK : MAIN_OFF_PEAK) : (peak ? LOCAL_PEAK : LOCAL_OFF_PEAK);
        double draw = random.nextDouble();
        if (draw < chances[0]) {
            return peak ? 1.6 + random.nextDouble() * 1.4 : 1.5 + random.nextDouble() * 0.7;
        }
        if (draw < chances[0] + chances[1]) {
            return 1.2 + random.nextDouble() * 0.25;
        }
        return 1.0 + random.nextDouble() * 0.15;
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
