package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.graph.CostFunction;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.service.VehiclePositionSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simulated GPS: moves each unit along its route, segment by segment, at the speed of the
 * route engine (the same CostFunction that estimated the route time, with the current
 * traffic of the graph). Every position is marked SIMULACION; it is never shown as a real GPS.
 *
 * <p>A closed segment stops the unit where it is until the route is recalculated (K8)
 * and {@link #follow} is called again with the new route.
 */
public class VehicleMovementSimulator implements VehiclePositionSource {

    private final RoadGraph graph;
    private final CostFunction cost;
    private final Clock clock;
    private final Map<Long, Trip> trips = new LinkedHashMap<>();

    public VehicleMovementSimulator(RoadGraph graph, CostFunction cost, Clock clock) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.cost = Objects.requireNonNull(cost, "cost");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts (or restarts) moving a unit along a route, from the start of its first segment.
     * A new route replaces the one the unit was following.
     */
    public synchronized void follow(long vehicleId, Route route, VehicleProfile vehicle) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(vehicle, "vehicle");
        trips.put(vehicleId, new Trip(vehicleId, edgesOf(route), vehicle, clock.instant()));
    }

    /** The unit stops being simulated (mission cancelled or finished). */
    public synchronized void stopFollowing(long vehicleId) {
        trips.remove(vehicleId);
    }

    /** True while the unit has not reached the end of its route. */
    public synchronized boolean isFollowing(long vehicleId) {
        return trips.containsKey(vehicleId);
    }

    /**
     * Moves every unit by the time passed since the last call and returns where they are.
     * A unit that reaches the end is reported there once, with speed 0, and is no longer followed.
     */
    @Override
    public synchronized List<VehicleLocation> poll() {
        Instant now = clock.instant();
        LocalDateTime at = LocalDateTime.ofInstant(now, clock.getZone());
        List<VehicleLocation> positions = new ArrayList<>();

        for (var iterator = trips.values().iterator(); iterator.hasNext(); ) {
            Trip trip = iterator.next();
            double seconds = Duration.between(trip.lastMove, now).toMillis() / 1000.0;
            trip.lastMove = now;
            double speedKmh = advance(trip, seconds);

            positions.add(new VehicleLocation(0, trip.vehicleId, at, position(trip),
                    round(speedKmh, 1), round(heading(trip), 1), PositionSource.SIMULATION));

            if (trip.arrived()) {
                iterator.remove();
            }
        }
        return positions;
    }

    // ------------------------------------------------------------------ movement

    /** Advances the trip by {@code seconds}; returns the current speed in km/h. */
    private double advance(Trip trip, double seconds) {
        while (!trip.arrived()) {
            Edge edge = graph.edge(trip.edges[trip.current]);
            double edgeSeconds = cost.seconds(edge, graph.state(edge.index()), trip.vehicle);
            if (Double.isInfinite(edgeSeconds)) {
                return 0.0;   // Closed segment: wait for a new route.
            }

            double metersPerSecond = edge.lengthM() / edgeSeconds;
            double secondsToEnd = (edge.lengthM() - trip.metersIntoEdge) / metersPerSecond;
            if (seconds < secondsToEnd) {
                trip.metersIntoEdge += seconds * metersPerSecond;
                return metersPerSecond * 3.6;
            }

            seconds -= secondsToEnd;
            trip.current++;
            trip.metersIntoEdge = 0;
        }
        return 0.0;
    }

    private GeoPoint position(Trip trip) {
        if (trip.arrived()) {
            Node end = graph.node(graph.edge(trip.edges[trip.edges.length - 1]).to());
            return point(end.lat(), end.lon());
        }
        Edge edge = graph.edge(trip.edges[trip.current]);
        Node from = graph.node(edge.from());
        Node to = graph.node(edge.to());
        double fraction = trip.metersIntoEdge / edge.lengthM();
        return point(from.lat() + (to.lat() - from.lat()) * fraction,
                from.lon() + (to.lon() - from.lon()) * fraction);
    }

    /** Direction of the current segment in degrees: 0 = north, 90 = east. */
    private double heading(Trip trip) {
        int index = trip.arrived() ? trip.edges.length - 1 : trip.current;
        Edge edge = graph.edge(trip.edges[index]);
        Node from = graph.node(edge.from());
        Node to = graph.node(edge.to());
        double lat1 = Math.toRadians(from.lat());
        double lat2 = Math.toRadians(to.lat());
        double deltaLon = Math.toRadians(to.lon() - from.lon());
        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    // ------------------------------------------------------------------ route → edges

    /** Edge indexes of the route in order; the segments must exist and be connected. */
    private int[] edgesOf(Route route) {
        List<RouteSegment> segments = new ArrayList<>(route.getSegments() == null ? List.of() : route.getSegments());
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("The route has no segments");
        }
        segments.sort(Comparator.comparingInt(RouteSegment::order));

        int[] edges = new int[segments.size()];
        for (int i = 0; i < edges.length; i++) {
            long segmentId = segments.get(i).segmentId();
            edges[i] = graph.edgeIndexOf(segmentId);
            if (edges[i] < 0) {
                throw new IllegalArgumentException("Segment " + segmentId + " is not in the road graph");
            }
            if (i > 0 && graph.edge(edges[i - 1]).to() != graph.edge(edges[i]).from()) {
                throw new IllegalArgumentException("Segments " + segments.get(i - 1).segmentId()
                        + " and " + segmentId + " are not connected");
            }
        }
        return edges;
    }

    private static GeoPoint point(double lat, double lon) {
        return new GeoPoint(round(lat, 6), round(lon, 6));
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /** Where one unit is on its route. */
    private static final class Trip {
        final long vehicleId;
        final int[] edges;
        final VehicleProfile vehicle;
        int current;
        double metersIntoEdge;
        Instant lastMove;

        Trip(long vehicleId, int[] edges, VehicleProfile vehicle, Instant start) {
            this.vehicleId = vehicleId;
            this.edges = edges;
            this.vehicle = vehicle;
            this.lastMove = start;
        }

        boolean arrived() {
            return current >= edges.length;
        }
    }
}