package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The simulator on a hand-made graph (invented streets, test data only):
 *
 * <pre>
 *            3
 *            |  203 (north)
 *   0 ------ 1 ------ 2      201: 0 -> 1, 202: 1 -> 2, 204: 2 -> 1
 * </pre>
 *
 * Every segment is 100 m at 36 km/h (10 m/s): an ambulance crosses one in 10 s.
 */
class VehicleMovementSimulatorTest {

    private static final double STEP = 0.0009;   // longitude between two nodes
    private static final double WEST = -73.2600;

    private final ManualClock clock = new ManualClock();
    private final RoadGraph graph = graph();
    private final VehicleMovementSimulator simulator =
            new VehicleMovementSimulator(graph, new EmergencyCost(), clock);

    private static RoadGraph graph() {
        List<Node> nodes = List.of(
                new Node(0, 1, 10.46, WEST),
                new Node(1, 2, 10.46, WEST + STEP),
                new Node(2, 3, 10.46, WEST + 2 * STEP),
                new Node(3, 4, 10.4609, WEST + STEP));
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(0, 201, 0, 1, 100, 36, "Calle de prueba"));
        edges.add(new Edge(1, 202, 1, 2, 100, 36, "Calle de prueba"));
        edges.add(new Edge(2, 203, 1, 3, 100, 36, "Carrera de prueba"));
        edges.add(new Edge(3, 204, 2, 1, 100, 36, "Calle de prueba"));
        return new RoadGraph(nodes, edges);
    }

    private static Route route(long... segmentIds) {
        List<RouteSegment> segments = new ArrayList<>();
        for (int i = 0; i < segmentIds.length; i++) {
            segments.add(new RouteSegment(i + 1, segmentIds[i], 10.0, 1.0, 0));
        }
        return new Route(0, 1, 5, null, RouteType.MAIN, 0.1 * segmentIds.length, 1.0, 1.0,
                LocalDateTime.of(2026, 11, 7, 10, 0), true, RouteStatus.ACTIVE, segments);
    }

    private VehicleLocation only() {
        List<VehicleLocation> positions = simulator.poll();
        assertEquals(1, positions.size());
        return positions.get(0);
    }

    @Test
    void theUnitMovesAlongItsRouteAtTheSpeedOfTheEngine() {
        simulator.follow(5, route(201, 202), VehicleProfile.AMBULANCE);

        VehicleLocation start = only();
        assertEquals(new GeoPoint(10.46, WEST), start.getLocation(), "it starts at the first node");
        assertEquals(PositionSource.SIMULATION, start.getSource(), "never shown as a real GPS");
        assertEquals(5, start.getVehicleId());
        assertEquals(LocalDateTime.of(2026, 11, 7, 10, 0), start.getRecordedAt());
        assertEquals(90.0, start.getHeadingDegrees(), 0.5, "going east");

        clock.advance(5);
        VehicleLocation half = only();
        assertEquals(WEST + STEP / 2, half.getLocation().longitude(), 1e-6, "half of the first segment");
        assertEquals(36.0, half.getSpeedKmh());

        clock.advance(10);
        assertEquals(WEST + 1.5 * STEP, only().getLocation().longitude(), 1e-6, "half of the second one");

        clock.advance(10);
        VehicleLocation end = only();
        assertEquals(new GeoPoint(10.46, -73.2582), end.getLocation(), "it arrived");
        assertEquals(0.0, end.getSpeedKmh());
        assertFalse(simulator.isFollowing(5), "it is reported once at the end and then forgotten");

        clock.advance(5);
        assertTrue(simulator.poll().isEmpty());
    }

    @Test
    void trafficOnTheGraphSlowsTheUnit() {
        graph.update(graph.edgeIndexOf(201), new EdgeState(3.0, false, 0, 1.0, 0));
        simulator.follow(5, route(201, 202), VehicleProfile.AMBULANCE);
        simulator.poll();

        clock.advance(10);
        VehicleLocation slow = only();

        // EmergencyCost: the ambulance feels 1 + (3 - 1) * (1 - 0.35) = 2.3 times the time: 23 s.
        assertEquals(WEST + STEP * 10 / 23, slow.getLocation().longitude(), 1e-6);
        assertEquals(15.7, slow.getSpeedKmh(), "100 m in 23 s");
    }

    @Test
    void aClosedSegmentStopsTheUnitUntilItGetsANewRoute() {
        graph.update(graph.edgeIndexOf(202), new EdgeState(1.0, true, 0, 1.0, 0));
        simulator.follow(5, route(201, 202), VehicleProfile.AMBULANCE);
        simulator.poll();

        clock.advance(30);
        VehicleLocation waiting = only();
        assertEquals(new GeoPoint(10.46, WEST + STEP), waiting.getLocation(), "it waits at node 1");
        assertEquals(0.0, waiting.getSpeedKmh());
        assertTrue(simulator.isFollowing(5));

        simulator.follow(5, route(203), VehicleProfile.AMBULANCE);   // recalculated route (K8)
        assertEquals(0.0, only().getHeadingDegrees(), 0.5, "now it goes north");
        clock.advance(5);
        assertEquals(10.46045, only().getLocation().latitude(), 1e-6);
    }

    @Test
    void routesThatDoNotMatchTheGraphAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> simulator.follow(5, route(999), VehicleProfile.AMBULANCE), "unknown segment");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.follow(5, route(202, 201), VehicleProfile.AMBULANCE), "not connected");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.follow(5, route(), VehicleProfile.AMBULANCE), "no segments");
        assertThrows(NullPointerException.class, () -> simulator.follow(5, null, VehicleProfile.AMBULANCE));
        assertFalse(simulator.isFollowing(5));
    }

    @Test
    void severalUnitsMoveIndependently() {
        simulator.follow(5, route(201, 202), VehicleProfile.AMBULANCE);
        simulator.follow(6, route(204), VehicleProfile.FIRE_TRUCK);
        assertEquals(2, simulator.poll().size());

        simulator.stopFollowing(5);
        clock.advance(5);
        List<VehicleLocation> positions = simulator.poll();
        assertEquals(1, positions.size());
        assertEquals(6, positions.get(0).getVehicleId());
        assertEquals(270.0, positions.get(0).getHeadingDegrees(), 0.5, "the fire truck goes west");
    }

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = LocalDateTime.of(2026, 11, 7, 10, 0).atZone(ZoneId.of("America/Bogota")).toInstant();

        void advance(long seconds) {
            now = now.plus(Duration.ofSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}