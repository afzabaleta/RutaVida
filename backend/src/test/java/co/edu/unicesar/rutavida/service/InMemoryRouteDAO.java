package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Fake RouteDAO for service tests: keeps routes in memory without Oracle. */
public class InMemoryRouteDAO implements RouteDAO {

    private final Map<Long, Route> routes = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public long insert(Route route) {
        long id = nextId++;
        route.setId(id);
        routes.put(id, route);
        return id;
    }

    @Override
    public Optional<Route> findById(long id) {
        return Optional.ofNullable(routes.get(id));
    }

    @Override
    public List<Route> findByEmergency(long emergencyId) {
        return routes.values().stream().filter(r -> r.getEmergencyId() == emergencyId).toList();
    }

    @Override
    public List<Route> findActive() {
        return routes.values().stream().filter(r -> r.getStatus() == RouteStatus.ACTIVE).toList();
    }

    @Override
    public List<Route> findActiveUsingSegments(Set<Long> segmentIds) {
        List<Route> found = new ArrayList<>();
        for (Route route : findActive()) {
            if (route.getSegments().stream().anyMatch(s -> segmentIds.contains(s.segmentId()))) {
                found.add(route);
            }
        }
        return found;
    }

    @Override
    public void updateStatus(long id, RouteStatus status) {
        throw new UnsupportedOperationException("Not needed by these tests");
    }

    /** Every route saved, in insertion order. */
    public List<Route> all() {
        return List.copyOf(routes.values());
    }
}
