package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteService on a small hand-made graph, without Oracle.
 *
 * <pre>
 *   2 ---------- 3        0 -> 2 -> 3 : fast avenue (60 km/h)
 *   |          / |        0 -> 1 -> 3 : slow streets (40 km/h)
 *   |        /   |        0 -> 3      : diagonal (30 km/h)
 *   0 ---------- 1        4 -> 0      : node 4 can leave but nobody reaches it
 * </pre>
 */
class RouteServiceTest {

    private static final GeoPoint P0 = new GeoPoint(10.4600, -73.2600);
    private static final GeoPoint P1 = new GeoPoint(10.4600, -73.2550);
    private static final GeoPoint P3 = new GeoPoint(10.4650, -73.2550);
    private static final GeoPoint P4 = new GeoPoint(10.4700, -73.2500);
    /** About 11 km away from every node: outside the network. */
    private static final GeoPoint FAR = new GeoPoint(10.5600, -73.2600);

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"),
            ZoneId.of("America/Bogota"));

    private RoadGraph graph;
    private InMemoryRouteDAO routeDao;
    private CountingTransactions transactions;
    private RouteService service;

    /** Fake TransactionManager: runs the work directly and counts the transactions. */
    private static final class CountingTransactions implements TransactionManager {
        private int calls;

        @Override
        public <T> T callInTransaction(Supplier<T> work) {
            calls++;
            return work.get();
        }
    }

    @BeforeEach
    void setUp() {
        graph = new RoadGraph(
                List.of(new Node(0, 100, P0.latitude(), P0.longitude()),
                        new Node(1, 101, P1.latitude(), P1.longitude()),
                        new Node(2, 102, 10.4650, -73.2600),
                        new Node(3, 103, P3.latitude(), P3.longitude()),
                        new Node(4, 104, P4.latitude(), P4.longitude())),
                List.of(new Edge(0, 900, 0, 1, 600, 40, "Calle de prueba"),
                        new Edge(1, 901, 1, 3, 600, 40, "Carrera de prueba"),
                        new Edge(2, 902, 0, 2, 600, 60, "Avenida de prueba"),
                        new Edge(3, 903, 2, 3, 600, 60, "Avenida de prueba"),
                        new Edge(4, 904, 0, 3, 900, 30, "Diagonal de prueba"),
                        new Edge(5, 905, 4, 0, 1000, 40, "Salida del nodo 4")));
        routeDao = new InMemoryRouteDAO();
        transactions = new CountingTransactions();
        service = new RouteService(graph, routeDao, transactions, CLOCK);
    }

    @Test
    void theRecommendedRouteComesFirstAndIsTheFastest() {
        List<RouteOption> options = service.calculateOptions(P0, P3, VehicleProfile.AMBULANCE);

        assertTrue(options.size() >= 2 && options.size() <= 3, "1 recommended + up to 2 alternatives");
        assertEquals(1, options.get(0).rank());
        assertEquals(RouteType.MAIN, options.get(0).type());
        assertEquals(List.of(902L, 903L), options.get(0).segmentIds(), "the avenue is the fastest");
        for (int i = 1; i < options.size(); i++) {
            assertEquals(i + 1, options.get(i).rank());
            assertEquals(RouteType.ALTERNATIVE, options.get(i).type());
            assertTrue(options.get(i).estimatedMinutes() >= options.get(0).estimatedMinutes());
            assertFalse(options.get(i).explanation().isBlank());
        }
    }

    @Test
    void congestionOnTheAvenueChangesTheRecommendedRoute() {
        graph.update(2, new EdgeState(4.0, false, 0, 1.0, 0));
        graph.update(3, new EdgeState(4.0, false, 0, 1.0, 0));

        List<RouteOption> options = service.calculateOptions(P0, P3, VehicleProfile.AMBULANCE);

        assertFalse(options.get(0).segmentIds().contains(902L), "the jammed avenue is no longer recommended");
    }

    @Test
    void aPointFarFromTheNetworkIsRejected() {
        assertFalse(service.isWithinNetwork(FAR));
        assertTrue(service.isWithinNetwork(P1));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.calculateOptions(P0, FAR, VehicleProfile.AMBULANCE));

        assertTrue(error.getMessage().contains("destino"));
    }

    @Test
    void theSameNodeOrNoWayAreBusinessErrors() {
        assertThrows(BusinessRuleException.class,
                () -> service.calculateOptions(P0, P0, VehicleProfile.AMBULANCE));
        assertThrows(BusinessRuleException.class,
                () -> service.calculateOptions(P0, P4, VehicleProfile.AMBULANCE), "nobody reaches node 4");
    }

    @Test
    void estimateSecondsMatchesTheRecommendedRoute() {
        double minutes = service.calculateOptions(P0, P3, VehicleProfile.AMBULANCE).get(0).estimatedMinutes();

        OptionalDouble seconds = service.estimateSeconds(P0, P3, VehicleProfile.AMBULANCE);

        assertTrue(seconds.isPresent());
        assertEquals(minutes * 60, seconds.getAsDouble(), 0.01);
        assertEquals(0, service.estimateSeconds(P0, P0, VehicleProfile.AMBULANCE).getAsDouble());
        assertTrue(service.estimateSeconds(P0, P4, VehicleProfile.AMBULANCE).isEmpty());
    }

    @Test
    void saveForMissionStoresTheRoutesInOneTransaction() {
        graph.update(2, new EdgeState(1.5, false, 20, 1.0, 0));
        List<RouteOption> options = service.calculateOptions(P0, P3, VehicleProfile.AMBULANCE);

        List<Route> saved = service.saveForMission(7, 3, null, VehicleProfile.AMBULANCE, options);

        assertEquals(1, transactions.calls);
        assertEquals(options.size(), saved.size());
        assertEquals(options.size(), routeDao.all().size());
        Route main = saved.get(0);
        assertTrue(main.getId() > 0);
        assertEquals(7, main.getEmergencyId());
        assertEquals(3, main.getVehicleId());
        assertTrue(main.isRecommended());
        assertEquals(RouteStatus.ACTIVE, main.getStatus());
        assertEquals(RouteType.MAIN, main.getType());
        assertEquals(LocalDateTime.now(CLOCK), main.getGeneratedAt());
        for (int i = 1; i < saved.size(); i++) {
            assertFalse(saved.get(i).isRecommended());
            assertEquals(RouteStatus.PROPOSED, saved.get(i).getStatus());
        }
        List<RouteSegment> segments = main.getSegments();
        assertEquals(options.get(0).segmentIds().size(), segments.size());
        assertEquals(1, segments.get(0).order());
        assertEquals(902L, segments.get(0).segmentId());
        assertEquals(1.5, segments.get(0).congestionFactor());
        assertEquals(Integer.valueOf(1), segments.get(0).trafficLightCount(),
                "the first segment ends in a traffic light");
        double sum = segments.stream().mapToDouble(RouteSegment::estimatedSeconds).sum();
        assertEquals(sum, main.getTotalCost(), 1e-9);
    }

    @Test
    void saveForMissionChecksItsInput() {
        List<RouteOption> options = service.calculateOptions(P0, P3, VehicleProfile.AMBULANCE);

        assertThrows(ValidationException.class,
                () -> service.saveForMission(0, 3, null, VehicleProfile.AMBULANCE, options));
        assertThrows(ValidationException.class,
                () -> service.saveForMission(7, 3, null, VehicleProfile.AMBULANCE, List.of()));
        assertThrows(ValidationException.class,
                () -> service.saveForMission(7, 3, null, VehicleProfile.AMBULANCE,
                        options.subList(1, options.size())),
                "the recommended route must come first");
        assertEquals(0, transactions.calls, "nothing is saved when the input is wrong");
    }

    @Test
    void vehicleProfileForTheTypesOfTheDatabase() {
        assertEquals(VehicleProfile.AMBULANCE, VehicleProfile.forType("AMBULANCIA"));
        assertEquals(VehicleProfile.FIRE_TRUCK, VehicleProfile.forType("BOMBERO"));
        assertThrows(IllegalArgumentException.class, () -> VehicleProfile.forType("PATRULLA"));
    }
}