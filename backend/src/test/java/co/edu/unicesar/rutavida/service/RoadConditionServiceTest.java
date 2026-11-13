package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.traffic.EdgeUpdate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RoadConditionService on the small city of DispatchTestSupport (edges 500, 501…), without Oracle. */
class RoadConditionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final RoadGraph graph = new DispatchTestSupport().graph;
    private final InMemoryRoadIncidentDAO incidents = new InMemoryRoadIncidentDAO();
    private final RoadConditionService conditions = new RoadConditionService(graph, incidents, CLOCK);

    /** Saves an incident in the given status on the segment with that Oracle id; returns it. */
    private RoadIncident incident(long segmentId, Severity severity, boolean blocks, IncidentStatus status) {
        RoadIncident incident = new RoadIncident(0, segmentId, IncidentType.ACCIDENT, severity, blocks, null,
                NOW, status == IncidentStatus.CLOSED ? NOW : null, status, IncidentOrigin.OPERATOR);
        incidents.insert(incident);
        return incident;
    }

    private EdgeState state(long segmentId) {
        return graph.state(graph.edgeIndexOf(segmentId));
    }

    @Test
    void onlyActiveIncidentsChangeTheGraph() {
        incident(500, Severity.HIGH, true, IncidentStatus.ACTIVE);
        incident(501, Severity.HIGH, true, IncidentStatus.PENDING);
        incident(502, Severity.HIGH, false, IncidentStatus.ACTIVE);

        assertEquals(Set.of(500L, 502L), conditions.syncIncidents());

        assertTrue(state(500).closed(), "it blocks the street");
        assertFalse(state(501).closed(), "RN-05: a pending incident changes nothing");
        assertFalse(state(502).closed());
        assertEquals(2.0, state(502).incidentFactor(), "HIGH without closing: twice as slow");
        assertTrue(conditions.syncIncidents().isEmpty(), "nothing new, nothing changed");
    }

    @Test
    void severalIncidentsOnOneSegmentKeepTheWorstOne() {
        incident(503, Severity.LOW, false, IncidentStatus.ACTIVE);
        incident(503, Severity.CRITICAL, false, IncidentStatus.ACTIVE);
        incident(504, Severity.MEDIUM, false, IncidentStatus.ACTIVE);
        incident(504, Severity.LOW, true, IncidentStatus.ACTIVE);

        conditions.syncIncidents();

        assertEquals(3.0, state(503).incidentFactor());
        assertFalse(state(503).closed());
        assertTrue(state(504).closed(), "one of them closes it");
    }

    @Test
    void whenTheIncidentEndsTheSegmentGoesBackToHowItWas() {
        RoadIncident accident = incident(500, Severity.HIGH, true, IncidentStatus.ACTIVE);
        RoadIncident roadwork = incident(501, Severity.MEDIUM, false, IncidentStatus.ACTIVE);
        int closedInOracle = graph.edgeIndexOf(505);
        graph.update(closedInOracle, new EdgeState(1.0, true, 0, 1.0, 0));
        incident(505, Severity.LOW, true, IncidentStatus.ACTIVE);
        conditions.syncIncidents();

        accident.setStatus(IncidentStatus.CLOSED);
        accident.setEndedAt(NOW);
        incidents.update(accident);
        roadwork.setStatus(IncidentStatus.CLOSED);
        roadwork.setEndedAt(NOW);
        incidents.update(roadwork);
        for (RoadIncident open : incidents.findByStatus(IncidentStatus.ACTIVE)) {
            open.setStatus(IncidentStatus.CLOSED);
            open.setEndedAt(NOW);
            incidents.update(open);
        }

        assertEquals(Set.of(500L, 501L, 505L), conditions.syncIncidents());

        assertFalse(state(500).closed(), "reopened");
        assertEquals(1.0, state(501).incidentFactor(), "no penalty");
        assertTrue(state(505).closed(), "it was CERRADO before the incident: it stays closed");
    }

    @Test
    void trafficChangesCongestionButNeverReopensAClosedSegment() {
        incident(500, Severity.HIGH, true, IncidentStatus.ACTIVE);
        incident(501, Severity.MEDIUM, false, IncidentStatus.ACTIVE);
        conditions.syncIncidents();

        Set<Long> changed = conditions.applyTraffic(List.of(
                new EdgeUpdate(graph.edgeIndexOf(500), 2.5, 10),
                new EdgeUpdate(graph.edgeIndexOf(501), 1.8, 0),
                new EdgeUpdate(graph.edgeIndexOf(502), 1.02, 0)));

        assertEquals(Set.of(500L, 501L), changed, "a change of 0.02 is not reported");
        assertTrue(state(500).closed(), "RN-06: only incidents close or reopen");
        assertEquals(2.5, state(500).congestion());
        assertEquals(10, state(500).signalDelaySec());
        assertEquals(1.5, state(501).incidentFactor(), "the penalty of the incident stays");
        assertEquals(1.8, state(501).congestion());
        assertEquals(1.02, state(502).congestion(), "it is applied even if it is not reported");
    }
}
