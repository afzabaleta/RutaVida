package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.dao.RoadIncidentDAO;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.model.TrafficReading;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.service.DispatchService;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryAlertDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoadIncidentDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.service.InMemoryVehicleLocationDAO;
import co.edu.unicesar.rutavida.service.RoadConditionService;
import co.edu.unicesar.rutavida.service.RouteMonitor;
import co.edu.unicesar.rutavida.service.RouteService;
import co.edu.unicesar.rutavida.service.TrackingService;
import co.edu.unicesar.rutavida.service.TrafficRecorder;
import co.edu.unicesar.rutavida.traffic.SimulatedTrafficProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;

/**
 * The simulation on the small city of DispatchTestSupport (K7), without Oracle: every part
 * of the cycle is real and the DAOs are in memory. The clock of the movement and the traffic
 * is moved by hand; random traffic only every hour, unless a test forces a segment.
 */
public final class SimulationTestSupport {

    public final ManualClock clock = new ManualClock(CLOCK.instant());
    public final DispatchTestSupport city = new DispatchTestSupport();
    public final DispatchService dispatch = city.service();
    public final InMemoryRoadIncidentDAO incidents = new InMemoryRoadIncidentDAO();
    public final InMemoryAlertDAO alerts = new InMemoryAlertDAO();
    public final InMemoryVehicleLocationDAO history = new InMemoryVehicleLocationDAO();
    public final List<TrafficReading> traffic = new ArrayList<>();
    public final VehicleMovementSimulator movement = new VehicleMovementSimulator(city.graph, new EmergencyCost(), clock);

    /** The engine with every part of the cycle. */
    public SimulationEngine engine() {
        return engine(incidents);
    }

    /** The same engine, with another source of incidents (for example, one that fails). */
    public SimulationEngine engine(RoadIncidentDAO incidentDao) {
        RouteMonitor monitor = new RouteMonitor(city.routes,
                new RouteService(city.graph, city.routes, city.transactions, CLOCK), city.vehicles,
                () -> DispatchTestSupport.VEHICLE_TYPES, city.assignments,
                new AlertService(alerts, new InMemoryUserDAO(), new InMemoryRoleDAO(), city.transactions, CLOCK),
                city.transactions);
        return new SimulationEngine(city.graph, new SimulatedTrafficProvider(city.graph, new Random(7), clock),
                new RoadConditionService(city.graph, incidentDao, clock),
                new TrafficRecorder(city.graph, traffic::addAll, city.transactions, clock), monitor, movement,
                new TrackingService(movement, city.vehicles, history, dispatch, city.transactions),
                dispatch, clock, Duration.ofSeconds(2), Duration.ofHours(1));
    }

    /** A clock that the tests move forward by hand. */
    public static final class ManualClock extends Clock {

        private Instant now;

        public ManualClock(Instant start) {
            this.now = start;
        }

        public void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return CLOCK.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
