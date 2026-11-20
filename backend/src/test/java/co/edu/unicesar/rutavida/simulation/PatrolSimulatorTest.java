package co.edu.unicesar.rutavida.simulation;

import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryVehicleLocationDAO;
import co.edu.unicesar.rutavida.service.TrackingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rounds of the police patrols (DT-10) on the 3 x 3 test city of DispatchTestSupport
 * (streets of 220 m), without Oracle: rounds of 200 to 700 m so they fit in the small city.
 */
class PatrolSimulatorTest {

    private static final long POLICE_TYPE = 3;

    private final SimulationTestSupport support = new SimulationTestSupport();
    private final InMemoryVehicleLocationDAO history = new InMemoryVehicleLocationDAO();
    private final VehicleMovementSimulator movement =
            new VehicleMovementSimulator(support.city.graph, new EmergencyCost(), support.clock);
    private final List<VehicleType> types = new ArrayList<>(DispatchTestSupport.VEHICLE_TYPES);
    private final PatrolSimulator patrols;
    private final long patrol;

    PatrolSimulatorTest() {
        types.add(new VehicleType(POLICE_TYPE, "POLICIA", 7, "Prueba"));
        patrol = support.city.vehicles.insert(new EmergencyVehicle(0, POLICE_TYPE, 1, "EJEMPLO-POL-01", null,
                VehicleOperationalStatus.AVAILABLE, DispatchTestSupport.at(0, 0),
                LocalDateTime.ofInstant(support.clock.instant(), support.clock.getZone()).minusMinutes(5)));
        TrackingService tracking = new TrackingService(movement, support.city.vehicles, history,
                support.dispatch, support.city.transactions);
        patrols = new PatrolSimulator(support.city.graph, movement, tracking, support.city.vehicles,
                () -> types, new EmergencyCost(), new Random(11), 200, 700);
    }

    private GeoPoint patrolAt() {
        return support.city.vehicles.findById(patrol).orElseThrow().getLastLocation();
    }

    @Test
    void aFreePatrolStartsARoundAndMovesWithSimulatedPositions() {
        patrols.tick();
        assertTrue(patrols.isPatrolling(patrol), "it chose a destination");

        GeoPoint before = patrolAt();
        support.clock.advance(8);
        patrols.tick();

        assertNotEquals(before, patrolAt(), "8 s at up to 50 km/h moves it");
        assertFalse(history.saved.isEmpty());
        assertTrue(history.saved.stream().allMatch(position -> position.getVehicleId() == patrol
                && position.getSource() == PositionSource.SIMULATION));
        assertEquals(VehicleOperationalStatus.AVAILABLE,
                support.city.vehicles.findById(patrol).orElseThrow().getOperationalStatus(),
                "a round is not a mission: the patrol stays DISPONIBLE");
    }

    @Test
    void whenItArrivesItStartsAnotherRoundSoItNeverStaysStill() {
        List<GeoPoint> seen = new ArrayList<>();
        for (int cycle = 0; cycle < 20; cycle++) {
            patrols.tick();
            seen.add(patrolAt());
            support.clock.advance(30);
        }
        // 20 cycles of 30 s are several rounds of 200 to 700 m: it keeps moving.
        long distinct = seen.stream().distinct().count();
        assertTrue(distinct >= 10, "it kept moving: " + distinct + " different places");
        // A patrol that just arrived starts its next round in the next cycle.
        patrols.tick();
        assertTrue(patrols.isPatrolling(patrol));
    }

    @Test
    void onlyFreePatrolsMoveAndAPatrolInMaintenanceStops() {
        patrols.tick();
        support.clock.advance(5);
        patrols.tick();
        List<Long> moved = history.saved.stream().map(VehicleLocation::getVehicleId).distinct().toList();
        assertEquals(List.of(patrol), moved, "ambulances and fire trucks are never moved by the rounds");

        support.city.vehicles.updateStatus(patrol, VehicleOperationalStatus.MAINTENANCE);
        patrols.tick();
        assertFalse(patrols.isPatrolling(patrol));
        GeoPoint stopped = patrolAt();
        support.clock.advance(30);
        patrols.tick();
        assertEquals(stopped, patrolAt(), "in MANTENIMIENTO it stays where it is");
    }

    @Test
    void withoutThePoliceTypeThereIsNothingToMove() {
        types.removeIf(type -> type.getId() == POLICE_TYPE);
        patrols.tick();
        assertFalse(patrols.isPatrolling(patrol));
        assertTrue(history.saved.isEmpty());
    }

    @Test
    void theEngineMovesThePatrolsOnlyWhileItRuns() {
        SimulationEngine engine = support.engineWith(patrols);
        engine.tick();
        support.clock.advance(8);
        engine.tick();
        assertTrue(patrols.isPatrolling(patrol), "a cycle of the engine includes the rounds");
        assertFalse(history.saved.isEmpty());
    }

    @Test
    void aRoundMustHaveAValidLength() {
        assertThrows(IllegalArgumentException.class, () -> new PatrolSimulator(support.city.graph, movement,
                new TrackingService(movement, support.city.vehicles, history, support.dispatch,
                        support.city.transactions), support.city.vehicles, () -> types, new EmergencyCost(),
                new Random(1), 500, 400));
    }
}
