package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TrackingService with a fake position source and in-memory DAOs, without Oracle. Invented units. */
class TrackingServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 7, 10, 0);
    private static final GeoPoint STATION = new GeoPoint(10.46858, -73.25759);
    private static final GeoPoint ON_THE_WAY = new GeoPoint(10.46800, -73.26000);

    /** What the source answers in the next poll. */
    private final List<VehicleLocation> next = new ArrayList<>();
    private final InMemoryEmergencyVehicleDAO vehicles = new InMemoryEmergencyVehicleDAO();
    private final InMemoryVehicleLocationDAO history = new InMemoryVehicleLocationDAO();
    private final EmergencyTestSupport.CountingTransactions transactions =
            new EmergencyTestSupport.CountingTransactions();
    private TrackingService tracking;
    private long ambulance;
    private long truck;

    @BeforeEach
    void setUp() {
        tracking = new TrackingService(() -> {
            List<VehicleLocation> polled = List.copyOf(next);
            next.clear();
            return polled;
        }, vehicles, history, DispatchTestSupport.rules(), transactions);

        ambulance = vehicles.insert(new EmergencyVehicle(0, 1, 1, "EJEMPLO-AMB-01", null,
                VehicleOperationalStatus.BUSY, STATION, T0.minusMinutes(5)));
        truck = vehicles.insert(new EmergencyVehicle(0, 2, 1, "EJEMPLO-BOM-01", null,
                VehicleOperationalStatus.AVAILABLE, null, null));
    }

    private static VehicleLocation at(long vehicleId, GeoPoint point, LocalDateTime time) {
        return new VehicleLocation(0, vehicleId, time, point, 36.0, 90.0, PositionSource.SIMULATION);
    }

    @Test
    void newPositionsAreSavedAsLastPositionAndHistoryInOneTransaction() {
        next.add(at(ambulance, ON_THE_WAY, T0));
        next.add(at(truck, STATION, T0));

        assertEquals(2, tracking.refreshPositions());

        assertEquals(ON_THE_WAY, vehicles.findById(ambulance).orElseThrow().getLastLocation());
        assertEquals(T0, vehicles.findById(ambulance).orElseThrow().getLastLocationAt());
        assertEquals(STATION, vehicles.findById(truck).orElseThrow().getLastLocation());
        assertEquals(2, history.saved.size());
        assertEquals(1, history.batches, "the whole history in one batch");
        assertEquals(1, transactions.calls, "one transaction per call");
        assertEquals(PositionSource.SIMULATION, history.saved.get(0).getSource());
    }

    @Test
    void aUnitThatDidNotMoveIsNotWrittenAgain() {
        next.add(at(ambulance, ON_THE_WAY, T0));
        tracking.refreshPositions();

        next.add(at(ambulance, ON_THE_WAY, T0.plusSeconds(5)));
        assertEquals(0, tracking.refreshPositions());

        assertEquals(1, history.saved.size());
        assertEquals(1, history.batches);
        assertEquals(1, vehicles.locationUpdates);
    }

    @Test
    void withoutNewPositionsNothingIsOpened() {
        assertEquals(0, tracking.refreshPositions());
        assertEquals(0, transactions.calls);
    }

    @Test
    void unknownUnitsAndOldPositionsAreSkippedWithoutStoppingTheOthers() {
        next.add(at(99, ON_THE_WAY, T0));
        next.add(at(ambulance, ON_THE_WAY, T0.minusHours(1)));   // older than the saved one
        next.add(at(truck, STATION, T0));

        assertEquals(1, tracking.refreshPositions());

        assertEquals(STATION, vehicles.findById(ambulance).orElseThrow().getLastLocation(), "not moved back");
        assertEquals(List.of(truck), history.saved.stream().map(VehicleLocation::getVehicleId).toList());
    }

    @Test
    void currentPositionAndHistory() {
        next.add(at(ambulance, ON_THE_WAY, T0));
        tracking.refreshPositions();

        assertEquals(Optional.of(ON_THE_WAY), tracking.currentPosition(ambulance));
        assertTrue(tracking.currentPosition(truck).isEmpty(), "it never reported a position");
        assertThrows(NotFoundException.class, () -> tracking.currentPosition(99));

        assertEquals(1, tracking.history(ambulance, T0.minusMinutes(1), T0.plusMinutes(1)).size());
        assertTrue(tracking.history(ambulance, T0.plusMinutes(1), T0.plusMinutes(2)).isEmpty());
        assertThrows(ValidationException.class, () -> tracking.history(ambulance, T0, T0.minusMinutes(1)));
        assertThrows(ValidationException.class, () -> tracking.history(ambulance, null, T0));
    }
}