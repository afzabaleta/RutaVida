package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadIncidentDAO;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.traffic.EdgeUpdate;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The only class that changes the state of the road graph in memory
 * (docs/convenciones-base-datos.md, section 5):
 * <ul>
 *   <li><b>Incidents (RN-05).</b> Only ACTIVO incidents count. One that blocks the street
 *   closes the segment; one that does not block it makes it slower according to its
 *   severity. When the incident is closed or rejected, the segment goes back to how it was.</li>
 *   <li><b>Traffic (RN-06).</b> The traffic source changes congestion and traffic lights,
 *   never "closed": a segment closed by an incident stays closed.</li>
 * </ul>
 * Each method returns the Oracle ids of the segments that changed, so RouteMonitor checks
 * only the active routes that use them. The cycle of the simulation (K9) calls this class.
 */
public class RoadConditionService {

    private static final Logger LOG = Logger.getLogger(RoadConditionService.class.getName());

    /** Congestion changes smaller than this are not reported as a change. */
    static final double CONGESTION_STEP = 0.05;

    /** What the incidents of one segment did to it, so it can be undone later. */
    private record Applied(boolean closes, double factor, boolean closedBefore) {
    }

    private final RoadGraph graph;
    private final RoadIncidentDAO incidentDao;
    private final Clock clock;
    private final Map<Integer, Applied> applied = new HashMap<>();

    public RoadConditionService(RoadGraph graph, RoadIncidentDAO incidentDao, Clock clock) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.incidentDao = Objects.requireNonNull(incidentDao, "incidentDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Puts the graph in line with the ACTIVO incidents of Oracle: applies the new ones and
     * undoes the ones that are no longer active. Safe to call at start and on every cycle.
     *
     * @return Oracle ids of the segments whose state changed
     */
    public synchronized Set<Long> syncIncidents() {
        Map<Integer, Applied> wanted = new HashMap<>();
        for (RoadIncident incident : incidentDao.findByStatus(IncidentStatus.ACTIVE)) {
            int edge = graph.edgeIndexOf(incident.getSegmentId());
            if (edge < 0) {
                LOG.fine(() -> "Incident " + incident.getId() + " is outside the loaded road network");
                continue;
            }
            Applied previous = wanted.get(edge);
            boolean closes = incident.isBlocksSegment() || (previous != null && previous.closes());
            double factor = Math.max(penaltyFor(incident.getSeverity()), previous == null ? 1.0 : previous.factor());
            wanted.put(edge, new Applied(closes, factor, false));
        }

        Set<Long> changed = new HashSet<>();
        for (Map.Entry<Integer, Applied> entry : Map.copyOf(applied).entrySet()) {
            if (!wanted.containsKey(entry.getKey())) {
                undo(entry.getKey(), entry.getValue());
                applied.remove(entry.getKey());
                changed.add(graph.edge(entry.getKey()).dbId());
            }
        }
        for (Map.Entry<Integer, Applied> entry : wanted.entrySet()) {
            int edge = entry.getKey();
            Applied now = entry.getValue();
            Applied before = applied.get(edge);
            if (before != null && before.closes() == now.closes() && before.factor() == now.factor()) {
                continue;
            }
            boolean closedBefore = before != null ? before.closedBefore() : graph.state(edge).closed();
            Applied result = new Applied(now.closes(), now.factor(), closedBefore);
            EdgeState state = graph.state(edge);
            graph.update(edge, new EdgeState(state.congestion(), closedBefore || result.closes(),
                    state.signalDelaySec(), result.factor(), clock.millis()));
            applied.put(edge, result);
            changed.add(graph.edge(edge).dbId());
        }
        return changed;
    }

    /**
     * Applies traffic changes (congestion and traffic light wait) and keeps the closures and
     * penalties of the incidents.
     *
     * @return Oracle ids of the segments whose congestion changed by at least CONGESTION_STEP
     */
    public synchronized Set<Long> applyTraffic(List<EdgeUpdate> updates) {
        Objects.requireNonNull(updates, "updates");
        Set<Long> changed = new HashSet<>();
        for (EdgeUpdate update : updates) {
            if (update.edgeIndex() >= graph.edgeCount()) {
                continue;
            }
            EdgeState state = graph.state(update.edgeIndex());
            graph.update(update.edgeIndex(), new EdgeState(update.congestion(), state.closed(),
                    update.signalDelaySec(), state.incidentFactor(), clock.millis()));
            if (Math.abs(update.congestion() - state.congestion()) >= CONGESTION_STEP) {
                changed.add(graph.edge(update.edgeIndex()).dbId());
            }
        }
        return changed;
    }

    /**
     * How much slower a segment is with an active incident that does not close it.
     * Initial values to calibrate with tests, like the ones of RN-08 and RN-09; not measured.
     */
    static double penaltyFor(Severity severity) {
        return switch (severity) {
            case LOW -> 1.2;
            case MEDIUM -> 1.5;
            case HIGH -> 2.0;
            case CRITICAL -> 3.0;
        };
    }

    private void undo(int edge, Applied done) {
        EdgeState state = graph.state(edge);
        graph.update(edge, new EdgeState(state.congestion(), done.closedBefore(), state.signalDelaySec(),
                1.0, clock.millis()));
    }
}
