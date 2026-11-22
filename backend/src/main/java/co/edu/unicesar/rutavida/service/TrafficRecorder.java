package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.TrafficReadingDAO;
import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.TrafficReading;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.traffic.EdgeUpdate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Saves the traffic of the simulation in TRAFICO, so the map (docs/api.md 8.2) and the
 * dashboard can read it. To avoid unnecessary writes, a segment is saved only when its level
 * changes (FLUIDO, MODERADO, CONGESTIONADO) or when its last reading is older than
 * {@link #REFRESH}; a segment that was always FLUIDO is never written.
 */
public class TrafficRecorder {

    /** Congestion from which a segment is MODERADO or CONGESTIONADO. Assumptions to calibrate. */
    static final double MODERATE_FROM = 1.2;
    static final double CONGESTED_FROM = RouteExplanationBuilder.CONGESTED_FROM;

    /** A level that does not change is saved again after this time (the dashboard looks 15 min back). */
    static final Duration REFRESH = Duration.ofMinutes(10);

    /** What was saved last for one segment. */
    private record Saved(TrafficLevel level, Instant at) {
    }

    private final RoadGraph graph;
    private final TrafficReadingDAO readingDao;
    private final TransactionManager transactions;
    private final Clock clock;
    private final Map<Integer, Saved> last = new HashMap<>();

    public TrafficRecorder(RoadGraph graph, TrafficReadingDAO readingDao, TransactionManager transactions,
                           Clock clock) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.readingDao = Objects.requireNonNull(readingDao, "readingDao");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The level of a congestion factor (1.0 = free flow). */
    public static TrafficLevel levelOf(double congestion) {
        if (congestion >= CONGESTED_FROM) {
            return TrafficLevel.CONGESTED;
        }
        return congestion >= MODERATE_FROM ? TrafficLevel.MODERATE : TrafficLevel.FLUID;
    }

    /** A congestion factor typical of a level, to force it in the demo (docs/api.md 10.3). */
    public static double congestionOf(TrafficLevel level) {
        return switch (level) {
            case FLUID -> 1.0;
            case MODERATE -> 1.3;
            case CONGESTED -> 2.5;
        };
    }

    /**
     * Saves the updates whose level changed or must be refreshed, in one batch.
     *
     * @return how many readings were saved
     */
    public synchronized int record(List<EdgeUpdate> updates, TrafficSource source) {
        Objects.requireNonNull(updates, "updates");
        Objects.requireNonNull(source, "source");
        Instant now = clock.instant();
        LocalDateTime at = LocalDateTime.ofInstant(now, clock.getZone());
        List<TrafficReading> readings = new ArrayList<>();
        Map<Integer, Saved> saved = new HashMap<>();

        for (EdgeUpdate update : updates) {
            if (update.edgeIndex() >= graph.edgeCount()) {
                continue;
            }
            TrafficLevel level = levelOf(update.congestion());
            Saved before = last.get(update.edgeIndex());
            boolean changed = before == null ? level != TrafficLevel.FLUID : before.level() != level;
            boolean old = before != null && Duration.between(before.at(), now).compareTo(REFRESH) >= 0;
            if (!changed && !old) {
                continue;
            }
            Edge edge = graph.edge(update.edgeIndex());
            double speed = Math.round(edge.freeFlowKmh() / update.congestion() * 100) / 100.0;
            readings.add(new TrafficReading(0, edge.dbId(), at, level, speed,
                    Math.round(update.congestion() * 100) / 100.0, source));
            saved.put(update.edgeIndex(), new Saved(level, now));
        }

        if (!readings.isEmpty()) {
            transactions.runInTransaction(() -> readingDao.insertBatch(readings));
            last.putAll(saved);
        }
        return readings.size();
    }
}
