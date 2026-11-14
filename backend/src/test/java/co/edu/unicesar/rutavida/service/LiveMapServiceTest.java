package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.TrackedMission;
import co.edu.unicesar.rutavida.dto.TrackedUnit;
import co.edu.unicesar.rutavida.dto.TrackingSnapshot;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static co.edu.unicesar.rutavida.service.EmergencyTestSupport.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The live map (docs/api.md 8.1) on in-memory DAOs, without Oracle. */
class LiveMapServiceTest {

    private TrackingTestSupport support;
    private UserSession coordinator;

    @BeforeEach
    void setUp() {
        support = new TrackingTestSupport();
        coordinator = EmergencyTestSupport.session("EMERGENCIAS:CONSULTAR", "DESPACHO:GESTIONAR");
    }

    @Test
    void everyUnitComesWithItsTypeStatusAndPosition() {
        TrackingSnapshot snapshot = support.service().snapshot(coordinator);

        assertEquals(5, snapshot.units().size());
        TrackedUnit ambulance = unit(snapshot, DispatchTestSupport.AMBULANCE);
        assertEquals("EJEMPLO-AMB-01", ambulance.unitCode());
        assertEquals("AMBULANCIA", ambulance.vehicleType());
        assertEquals(VehicleOperationalStatus.AVAILABLE, ambulance.operationalStatus());
        assertEquals(DispatchTestSupport.at(1, 1), ambulance.location());
        assertEquals(PositionSource.SIMULATION, ambulance.source());
        assertEquals("BOMBERO", unit(snapshot, DispatchTestSupport.FIRE_TRUCK).vehicleType());

        TrackedUnit withoutPosition = unit(snapshot, DispatchTestSupport.NO_POSITION_AMBULANCE);
        assertNull(withoutPosition.location());
        assertNull(withoutPosition.source(), "no position, no source");
        assertEquals(LocalDateTime.now(CLOCK), snapshot.serverTime());
    }

    @Test
    void aDispatchedUnitShowsItsMissionAndTheRouteItFollows() {
        long emergency = support.city.report(DispatchTestSupport.MEDICAL, 1, 2);
        long assignment = support.city.service()
                .dispatch(emergency, DispatchTestSupport.AMBULANCE, coordinator).assignment().getId();

        TrackingSnapshot snapshot = support.service().snapshot(coordinator);

        assertEquals(VehicleOperationalStatus.BUSY, unit(snapshot, DispatchTestSupport.AMBULANCE).operationalStatus());
        TrackedMission mission = snapshot.missions().stream()
                .filter(m -> m.assignmentId() == assignment).findFirst().orElseThrow();
        assertEquals(emergency, mission.emergencyId());
        assertEquals(DispatchTestSupport.AMBULANCE, mission.vehicleId());
        assertEquals(AssignmentStatus.ASSIGNED, mission.status());
        long active = support.city.routes.findByEmergency(emergency).stream()
                .filter(r -> r.isRecommended()).findFirst().orElseThrow().getId();
        assertEquals(active, mission.activeRouteId());
        assertEquals(support.city.routes.findByEmergency(emergency).size(), mission.routesVersion());
        assertTrue(mission.routesVersion() >= 1);
    }

    @Test
    void theBusyUnitOfTheTestCityHasNoOpenMissionSoNoneIsInvented() {
        TrackingSnapshot snapshot = support.service().snapshot(coordinator);

        assertEquals(VehicleOperationalStatus.BUSY,
                unit(snapshot, DispatchTestSupport.BUSY_AMBULANCE).operationalStatus());
        assertTrue(snapshot.missions().isEmpty());
    }

    @Test
    void theUnreadAlertsAreOnlyThoseOfTheUserAndTheSimulationFlagComesFromK9() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        support.alerts.insert(new Alert(0, null, EmergencyTestSupport.OPERATOR_ID, AlertType.OTHER,
                "Alerta de EJEMPLO.", now, false));
        support.alerts.insert(new Alert(0, null, EmergencyTestSupport.OPERATOR_ID, AlertType.OTHER,
                "Alerta de EJEMPLO ya leída.", now, true));
        support.alerts.insert(new Alert(0, null, 99, AlertType.OTHER, "De otro usuario.", now, false));

        TrackingSnapshot stopped = support.service().snapshot(coordinator);
        assertEquals(1, stopped.unreadAlerts());
        assertFalse(stopped.simulation().running());
        assertEquals(PositionSource.SIMULATION, stopped.simulation().source());

        support.running = true;
        assertTrue(support.service().snapshot(coordinator).simulation().running());
    }

    private static TrackedUnit unit(TrackingSnapshot snapshot, long vehicleId) {
        return snapshot.units().stream().filter(u -> u.vehicleId() == vehicleId).findFirst().orElseThrow();
    }
}
