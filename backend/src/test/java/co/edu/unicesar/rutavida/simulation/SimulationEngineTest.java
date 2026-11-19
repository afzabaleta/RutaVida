package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.dao.RoadIncidentDAO;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static co.edu.unicesar.rutavida.service.DispatchTestSupport.AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.CONFIRMED_HOSPITAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.MEDICAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cycle of the simulation (K9) on the small city of DispatchTestSupport, without Oracle:
 * the ambulance at (1,1) is sent to an emergency at (1,2), one street of 220 m.
 */
class SimulationEngineTest {

    private static final UserSession COORDINATOR = EmergencyTestSupport.session("DESPACHO:GESTIONAR");

    private final SimulationTestSupport support = new SimulationTestSupport();
    private final SimulationEngine engine = support.engine();

    /** Dispatches the ambulance to a new medical emergency at (1,2); returns the assignment id. */
    private long dispatchAmbulance() {
        long emergency = support.city.report(MEDICAL, 1, 2);
        return support.dispatch.dispatch(emergency, AMBULANCE, COORDINATOR).assignment().getId();
    }

    private AssignmentStatus status(long mission) {
        return support.city.assignments.findById(mission).orElseThrow().getStatus();
    }

    private GeoPoint ambulanceAt() {
        return support.city.vehicles.findById(AMBULANCE).orElseThrow().getLastLocation();
    }

    /** Same place: the simulator rounds to 6 decimals, like the NUMBER(9,6) columns. */
    private static void assertNear(GeoPoint expected, GeoPoint actual, String message) {
        assertTrue(Math.abs(expected.latitude() - actual.latitude()) < 1e-6
                && Math.abs(expected.longitude() - actual.longitude()) < 1e-6, message + ": " + actual);
    }

    private Optional<Route> activeRoute() {
        return support.city.routes.all().stream().filter(route -> route.getStatus() == RouteStatus.ACTIVE).findFirst();
    }

    @Test
    void aDispatchedUnitLeavesMovesAndArrivesOnScene() {
        long mission = dispatchAmbulance();

        engine.tick();
        assertEquals(AssignmentStatus.EN_ROUTE, status(mission), "the unit leaves in the first cycle");
        assertTrue(support.movement.isFollowing(AMBULANCE));

        support.clock.advance(10);
        engine.tick();
        double longitude = ambulanceAt().longitude();
        assertTrue(longitude > DispatchTestSupport.at(1, 1).longitude()
                && longitude < DispatchTestSupport.at(1, 2).longitude(), "on its way: " + ambulanceAt());
        assertEquals(AssignmentStatus.EN_ROUTE, status(mission));

        support.clock.advance(60);
        engine.tick();
        assertNear(DispatchTestSupport.at(1, 2), ambulanceAt(), "at the emergency");
        assertEquals(AssignmentStatus.ON_SCENE, status(mission), "marked by the simulation (docs/api.md 6.6)");
        assertTrue(activeRoute().isEmpty(), "the route to the scene ended");

        List<VehicleLocation> saved = List.copyOf(support.history.saved);
        assertTrue(saved.size() >= 2, "on its way and at the end");
        assertTrue(saved.stream().allMatch(position -> position.getSource() == PositionSource.SIMULATION));

        engine.tick();
        assertFalse(support.movement.isFollowing(AMBULANCE));
        assertEquals(saved.size(), support.history.saved.size(), "a unit that does not move is not written again");
    }

    @Test
    void afterTheTransferTheAmbulanceDrivesToTheHospital() {
        long mission = dispatchAmbulance();
        engine.tick();
        support.clock.advance(70);
        engine.tick();
        assertEquals(AssignmentStatus.ON_SCENE, status(mission), "first it reaches the scene at (1,2)");

        support.dispatch.startTransfer(mission, CONFIRMED_HOSPITAL, COORDINATOR);
        engine.tick();
        Route toHospital = activeRoute().orElseThrow();
        assertEquals(CONFIRMED_HOSPITAL, toHospital.getDestinationHospitalId(), "the new ACTIVA route of K10");
        assertTrue(support.movement.isFollowing(AMBULANCE), "K9 follows the route to the hospital");

        support.clock.advance(600);
        engine.tick();
        engine.tick();
        assertNear(DispatchTestSupport.at(0, 0), ambulanceAt(), "at the hospital");
        assertEquals(AssignmentStatus.TRANSPORTING, status(mission),
                "the simulation does not mark it EN_SITIO again: the coordinator ends the mission");

        support.dispatch.advance(mission, AssignmentStatus.FINISHED);
        assertEquals(VehicleOperationalStatus.AVAILABLE,
                support.city.vehicles.findById(AMBULANCE).orElseThrow().getOperationalStatus());
        engine.tick();
        assertFalse(support.movement.isFollowing(AMBULANCE), "a finished mission is not followed");
    }

    @Test
    void aClosedStreetMakesTheUnitTakeTheRecalculatedRoute() {
        long mission = dispatchAmbulance();
        engine.tick();
        Route first = activeRoute().orElseThrow();
        long street = first.getSegments().get(0).segmentId();
        support.incidents.insert(new RoadIncident(0, street, IncidentType.ACCIDENT, Severity.HIGH, true,
                "Prueba", LocalDateTime.now(support.clock), null, IncidentStatus.ACTIVE, IncidentOrigin.OPERATOR));

        engine.tick();
        Route detour = activeRoute().orElseThrow();
        assertNotEquals(first.getId(), detour.getId(), "K8 replaced the route");
        assertFalse(detour.getSegments().stream().anyMatch(segment -> segment.segmentId() == street));
        assertEquals(AssignmentStatus.EN_ROUTE, status(mission));

        support.clock.advance(180);
        engine.tick();
        assertEquals(AssignmentStatus.ON_SCENE, status(mission), "it arrived by the detour");
        assertNear(DispatchTestSupport.at(1, 2), ambulanceAt(), "at the emergency");
    }

    @Test
    void forcedTrafficIsAppliedAndSavedInTheNextCycle() {
        long segment = support.city.graph.edge(0).dbId();

        engine.forceCongestion(segment, TrafficLevel.CONGESTED);
        engine.tick();

        int edge = support.city.graph.edgeIndexOf(segment);
        assertEquals(2.5, support.city.graph.state(edge).congestion(), 1e-9);
        assertTrue(support.traffic.stream().anyMatch(reading -> reading.getSegmentId() == segment
                && reading.getLevel() == TrafficLevel.CONGESTED && reading.getSource() == TrafficSource.SIMULATION));
        assertThrows(NotFoundException.class, () -> engine.forceCongestion(99999, TrafficLevel.CONGESTED));
    }

    @Test
    void theRandomTrafficChangesOnlyEveryTrafficPeriod() {
        engine.tick();
        assertTrue(congestions().stream().allMatch(value -> value == 1.0), "not yet: once an hour in the tests");

        support.clock.advance(3600);
        engine.tick();
        assertTrue(congestions().stream().anyMatch(value -> value > 1.0), "after an hour, some streets changed");
    }

    private List<Double> congestions() {
        return IntStream.range(0, support.city.graph.edgeCount())
                .mapToObj(edge -> support.city.graph.state(edge).congestion()).toList();
    }

    @Test
    void aStepThatFailsDoesNotStopTheOthers() {
        RoadIncidentDAO broken = new RoadIncidentDAO() {
            @Override
            public long insert(RoadIncident incident) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<RoadIncident> findById(long id) {
                return Optional.empty();
            }

            @Override
            public List<RoadIncident> findByStatus(IncidentStatus status) {
                throw new DataAccessException("ORA-12541: TNS:no listener", null);
            }

            @Override
            public void update(RoadIncident incident) {
                throw new UnsupportedOperationException();
            }
        };
        SimulationEngine withoutIncidents = support.engine(broken);
        long mission = dispatchAmbulance();

        withoutIncidents.tick();
        support.clock.advance(10);
        withoutIncidents.tick();

        assertEquals(AssignmentStatus.EN_ROUTE, status(mission), "the missions still moved");
        assertFalse(support.history.saved.isEmpty(), "and the positions were saved");
    }

    @Test
    void startAndStopAreNotErrorsWhenRepeated() {
        assertFalse(engine.isRunning());
        assertNull(engine.startedAt());

        engine.start();
        engine.start();
        assertTrue(engine.isRunning());
        assertEquals(LocalDateTime.now(support.clock), engine.startedAt());
        assertEquals(2, engine.tickSeconds());
        assertEquals(TrafficSource.SIMULATION, engine.source());

        engine.stop();
        engine.stop();
        assertFalse(engine.isRunning());
        assertNull(engine.startedAt());
    }
}
