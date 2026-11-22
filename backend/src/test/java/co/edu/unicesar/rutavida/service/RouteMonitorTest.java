package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.traffic.EdgeUpdate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static co.edu.unicesar.rutavida.service.DispatchTestSupport.ACCIDENT;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.FIRE_TRUCK;
import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteMonitor on the small city of DispatchTestSupport, without Oracle: a unit is
 * dispatched with K7, the road changes and the route is (or is not) replaced.
 */
class RouteMonitorTest {

    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final UserSession COORDINATOR = EmergencyTestSupport.session("DESPACHO:GESTIONAR");

    private final DispatchTestSupport city = new DispatchTestSupport();
    private final DispatchService dispatch = city.service();
    private final InMemoryRoadIncidentDAO incidents = new InMemoryRoadIncidentDAO();
    private final InMemoryAlertDAO alerts = new InMemoryAlertDAO();
    private final RoadConditionService conditions = new RoadConditionService(city.graph, incidents, CLOCK);
    private final RouteMonitor monitor = new RouteMonitor(city.routes,
            new RouteService(city.graph, city.routes, city.transactions, CLOCK), city.vehicles,
            () -> DispatchTestSupport.VEHICLE_TYPES, city.assignments,
            new AlertService(alerts, new InMemoryUserDAO(), new InMemoryRoleDAO(), city.transactions, CLOCK),
            city.transactions);

    /** Dispatches the unit to an accident at the node (1,2) and returns its ACTIVA route. */
    private Route dispatched(long vehicleId) {
        long accident = city.report(ACCIDENT, 1, 2);
        DispatchResult result = dispatch.dispatch(accident, vehicleId, COORDINATOR);
        return result.routes().get(0);
    }

    /** Oracle id of the street that goes from node (r1,c1) to node (r2,c2). */
    private long street(int r1, int c1, int r2, int c2) {
        for (int i = 0; i < city.graph.edgeCount(); i++) {
            Edge edge = city.graph.edge(i);
            if (edge.from() == r1 * 3 + c1 && edge.to() == r2 * 3 + c2) {
                return edge.dbId();
            }
        }
        throw new IllegalArgumentException("no such street");
    }

    private void closeStreet(long segmentId) {
        incidents.insert(new RoadIncident(0, segmentId, IncidentType.ACCIDENT, Severity.HIGH, true, null,
                NOW, null, IncidentStatus.ACTIVE, IncidentOrigin.OPERATOR));
    }

    private List<Route> withStatus(RouteStatus status) {
        return city.routes.all().stream().filter(route -> route.getStatus() == status).toList();
    }

    private static List<Long> segments(Route route) {
        return route.getSegments().stream().map(RouteSegment::segmentId).toList();
    }

    @Test
    void aClosedStreetAheadReplacesTheRouteAndTellsTheCoordinator() {
        Route old = dispatched(AMBULANCE);
        long blocked = street(1, 1, 1, 2);
        assertEquals(List.of(blocked), segments(old), "one street from the ambulance to the accident");
        closeStreet(blocked);

        List<Route> replaced = monitor.checkActiveRoutes(conditions.syncIncidents());

        assertEquals(1, replaced.size());
        Route now = replaced.get(0);
        assertEquals(RouteStatus.ACTIVE, now.getStatus());
        assertFalse(segments(now).contains(blocked), "the new route avoids the closed street");
        assertEquals(List.of(now.getId()),
                withStatus(RouteStatus.ACTIVE).stream().map(Route::getId).toList(),
                "only one ACTIVA route for the mission");
        assertTrue(withStatus(RouteStatus.DISCARDED).stream().anyMatch(route -> route.getId() == old.getId()));
        Alert alert = alerts.all().get(0);
        assertEquals(EmergencyTestSupport.OPERATOR_ID, alert.getUserId(), "the coordinator who dispatched it");
        assertEquals(AlertType.ROUTE_RECALCULATION, alert.getType());
        assertEquals("Se recalculó la ruta de EJEMPLO-AMB-01 porque la anterior ya no se puede usar.",
                alert.getMessage());
    }

    @Test
    void heavyTrafficReplacesTheRouteWhenTheNewOneIsMuchFaster() {
        Route old = dispatched(FIRE_TRUCK);
        List<EdgeUpdate> jam = segments(old).stream()
                .map(id -> new EdgeUpdate(city.graph.edgeIndexOf(id), 5.0, 0))
                .toList();

        Optional<Route> now = monitor.checkActiveRoutes(conditions.applyTraffic(jam)).stream().findFirst();

        assertTrue(now.isPresent());
        assertNotEquals(segments(old), segments(now.get()), "another way, around the jam");
        assertTrue(alerts.all().get(0).getMessage().contains("min antes"), "it says how much it saves");
    }

    @Test
    void aSmallChangeKeepsTheRoute() {
        Route old = dispatched(FIRE_TRUCK);
        List<EdgeUpdate> light = segments(old).stream()
                .map(id -> new EdgeUpdate(city.graph.edgeIndexOf(id), 1.1, 0))
                .toList();

        assertTrue(monitor.checkActiveRoutes(conditions.applyTraffic(light)).isEmpty(),
                "RN-09: a few seconds do not change the route");
        assertEquals(RouteStatus.ACTIVE, city.routes.all().get(0).getStatus());
        assertTrue(alerts.all().isEmpty());
    }

    @Test
    void aStreetAlreadyPassedDoesNotMatter() {
        Route old = dispatched(FIRE_TRUCK);
        List<Long> path = segments(old);
        Node lastStreetStart = city.graph.node(city.graph.edge(city.graph.edgeIndexOf(path.get(path.size() - 1))).from());
        city.vehicles.updateLocation(FIRE_TRUCK, new GeoPoint(lastStreetStart.lat(), lastStreetStart.lon()),
                NOW.plusMinutes(1));
        closeStreet(path.get(0));

        assertTrue(monitor.checkActiveRoutes(conditions.syncIncidents()).isEmpty(),
                "the unit is on the last street: the closed first one is behind it");
        assertEquals(RouteStatus.ACTIVE, city.routes.all().get(0).getStatus());
    }

    @Test
    void theRuleOfRn09() {
        assertTrue(RouteMonitor.shouldReplace(100, 84), "saves 16 %");
        assertFalse(RouteMonitor.shouldReplace(100, 86), "saves 14 % and 14 s");
        assertTrue(RouteMonitor.shouldReplace(1000, 965), "saves 35 s");
        assertFalse(RouteMonitor.shouldReplace(1000, 975), "saves 25 s and 2.5 %");
        assertTrue(monitor.checkActiveRoutes(Set.of()).isEmpty());
    }
}
