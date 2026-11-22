package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.dto.UnitCandidate;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static co.edu.unicesar.rutavida.service.DispatchTestSupport.ACCIDENT;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.BUSY_AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.FAR_AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.FIRE_TRUCK;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.MEDICAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.NO_POSITION_AMBULANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dispatch use cases (CU-08, CU-09, CU-10) on the small city of DispatchTestSupport, without Oracle. */
class DispatchMissionTest {

    private static final LocalDateTime NOW = LocalDateTime.now(EmergencyTestSupport.CLOCK);
    private static final UserSession COORDINATOR = EmergencyTestSupport.session("DESPACHO:GESTIONAR");

    private final DispatchTestSupport city = new DispatchTestSupport();
    private final DispatchService dispatch = city.service();

    private static Set<Long> ids(List<UnitCandidate> candidates) {
        return candidates.stream().map(UnitCandidate::vehicleId).collect(Collectors.toSet());
    }

    private VehicleOperationalStatus unitStatus(long vehicleId) {
        return city.vehicles.findById(vehicleId).orElseThrow().getOperationalStatus();
    }

    private EmergencyStatus emergencyStatus(long emergencyId) {
        return city.emergencies.findById(emergencyId).orElseThrow().getStatus();
    }

    // ------------------------------------------------------------------ CU-09: candidates

    @Test
    void candidatesAreAvailableSuitableUnitsFastestFirst() {
        long accident = city.report(ACCIDENT, 1, 2);

        List<UnitCandidate> candidates = dispatch.candidates(accident);

        assertEquals(AMBULANCE, candidates.get(0).vehicleId(), "one street away: the fastest");
        assertEquals(0.22, candidates.get(0).distanceKm(), 0.01);
        assertEquals(Set.of(AMBULANCE, FIRE_TRUCK, NO_POSITION_AMBULANCE), ids(candidates),
                "not the busy unit, not the one outside the road network");
        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).etaMinutes() <= candidates.get(i).etaMinutes(), "sorted by ETA");
        }
        UnitCandidate fromStation = candidates.stream()
                .filter(candidate -> candidate.vehicleId() == NO_POSITION_AMBULANCE).findFirst().orElseThrow();
        assertEquals(DispatchTestSupport.at(2, 0), fromStation.location(), "without position it leaves from its station");
        assertEquals("EJEMPLO - Base de prueba", fromStation.stationName());

        long medical = city.report(MEDICAL, 1, 0);
        assertEquals(Set.of(AMBULANCE, NO_POSITION_AMBULANCE), ids(dispatch.candidates(medical)),
                "a medical emergency is attended only by ambulances");
    }

    @Test
    void candidatesOnlyForAReportedEmergency() {
        long accident = city.report(ACCIDENT, 1, 2);
        dispatch.dispatch(accident, AMBULANCE, COORDINATOR);

        assertThrows(BusinessRuleException.class, () -> dispatch.candidates(accident), "it is ASIGNADA now");
        assertThrows(NotFoundException.class, () -> dispatch.candidates(99));
    }

    // ------------------------------------------------------------------ CU-08: preview

    @Test
    void previewCalculatesTheRoutesWithoutSavingThem() {
        long accident = city.report(ACCIDENT, 1, 2);

        List<RouteOption> options = dispatch.preview(accident, NO_POSITION_AMBULANCE);

        assertFalse(options.isEmpty());
        assertEquals(1, options.get(0).rank(), "the recommended route first");
        assertTrue(city.routes.all().isEmpty(), "nothing is saved");
        assertEquals(VehicleOperationalStatus.AVAILABLE, unitStatus(NO_POSITION_AMBULANCE));
        assertThrows(BusinessRuleException.class, () -> dispatch.preview(accident, FAR_AMBULANCE),
                "outside the road network");
    }

    // ------------------------------------------------------------------ CU-09: dispatch

    @Test
    void aDispatchCreatesTheAssignmentTheRoutesAndTheAudit() {
        long accident = city.report(ACCIDENT, 1, 2);

        DispatchResult result = dispatch.dispatch(accident, AMBULANCE, COORDINATOR);

        EmergencyAssignment assignment = result.assignment();
        assertEquals(AssignmentStatus.ASSIGNED, assignment.getStatus());
        assertEquals(EmergencyTestSupport.OPERATOR_ID, assignment.getCoordinatorUserId(), "the user of the session");
        assertEquals(NOW, assignment.getAssignedAt());
        assertEquals(EmergencyStatus.ASSIGNED, emergencyStatus(accident));
        assertEquals(VehicleOperationalStatus.BUSY, unitStatus(AMBULANCE));

        List<Route> routes = result.routes();
        assertFalse(routes.isEmpty());
        assertEquals(RouteStatus.ACTIVE, routes.get(0).getStatus(), "the recommended route is the one it drives");
        assertTrue(routes.get(0).isRecommended());
        assertTrue(routes.stream().skip(1).allMatch(route -> route.getStatus() == RouteStatus.PROPOSED));
        assertEquals(routes.size(), city.routes.all().size());

        AuditLog audit = city.audit.last();
        assertEquals("DESPACHO", audit.getModule());
        assertEquals("DESPACHAR", audit.getOperation());
        assertEquals(assignment.getId(), audit.getRecordId());
    }

    @Test
    void aDispatchThatBreaksARuleSavesNothing() {
        long accident = city.report(ACCIDENT, 1, 2);
        long medical = city.report(MEDICAL, 1, 0);

        assertThrows(BusinessRuleException.class, () -> dispatch.dispatch(accident, BUSY_AMBULANCE, COORDINATOR),
                "the unit is OCUPADO");
        assertThrows(BusinessRuleException.class, () -> dispatch.dispatch(medical, FIRE_TRUCK, COORDINATOR),
                "a fire truck does not attend a medical emergency");
        assertThrows(BusinessRuleException.class, () -> dispatch.dispatch(accident, FAR_AMBULANCE, COORDINATOR),
                "no route by the road network");
        assertThrows(NotFoundException.class, () -> dispatch.dispatch(99, AMBULANCE, COORDINATOR));
        assertThrows(NotFoundException.class, () -> dispatch.dispatch(accident, 99, COORDINATOR));

        assertTrue(city.assignments.findByEmergency(accident).isEmpty());
        assertTrue(city.routes.all().isEmpty());
        assertEquals(EmergencyStatus.REPORTED, emergencyStatus(accident));

        dispatch.dispatch(accident, AMBULANCE, COORDINATOR);
        assertThrows(BusinessRuleException.class, () -> dispatch.dispatch(accident, NO_POSITION_AMBULANCE, COORDINATOR),
                "the emergency is no longer REPORTADA");
    }

    // ------------------------------------------------------------------ CU-10: the mission advances

    @Test
    void theMissionAdvancesAndTheEmergencyTheRoutesAndTheUnitFollow() {
        long accident = city.report(ACCIDENT, 1, 2);
        long mission = dispatch.dispatch(accident, AMBULANCE, COORDINATOR).assignment().getId();

        EmergencyAssignment onTheWay = dispatch.advance(mission, AssignmentStatus.EN_ROUTE);
        assertEquals(NOW, onTheWay.getDepartedAt());
        assertEquals(EmergencyStatus.EN_ROUTE, emergencyStatus(accident));
        assertFalse(dispatch.missionRoutes(mission).isEmpty());

        EmergencyAssignment arrived = dispatch.advance(mission, AssignmentStatus.ON_SCENE);
        assertEquals(NOW, arrived.getArrivedAt());
        assertEquals(EmergencyStatus.ON_SCENE, emergencyStatus(accident));
        assertEquals(RouteStatus.FINISHED, city.routes.all().get(0).getStatus(), "the route it drove");
        assertTrue(city.routes.all().stream().skip(1).allMatch(route -> route.getStatus() == RouteStatus.DISCARDED));
        assertTrue(dispatch.missionRoutes(mission).isEmpty());

        assertThrows(ValidationException.class, () -> dispatch.advance(mission, AssignmentStatus.TRANSPORTING),
                "the transfer needs the hospital (CU-11)");

        dispatch.advance(mission, AssignmentStatus.FINISHED);
        assertEquals(VehicleOperationalStatus.AVAILABLE, unitStatus(AMBULANCE), "free again");
        assertEquals(EmergencyStatus.ON_SCENE, emergencyStatus(accident), "it is closed later (CU-12)");
        assertEquals(AssignmentStatus.FINISHED, city.assignments.findById(mission).orElseThrow().getStatus());

        assertThrows(BusinessRuleException.class, () -> dispatch.advance(mission, AssignmentStatus.EN_ROUTE));
        assertThrows(NotFoundException.class, () -> dispatch.advance(99, AssignmentStatus.EN_ROUTE));
    }

    @Test
    void cancellingFreesTheUnitAndTheEmergencyWaitsForAnotherOne() {
        long accident = city.report(ACCIDENT, 1, 2);
        long mission = dispatch.dispatch(accident, AMBULANCE, COORDINATOR).assignment().getId();

        dispatch.advance(mission, AssignmentStatus.CANCELLED);

        assertEquals(VehicleOperationalStatus.AVAILABLE, unitStatus(AMBULANCE));
        assertEquals(EmergencyStatus.REPORTED, emergencyStatus(accident));
        assertTrue(city.routes.all().stream().allMatch(route -> route.getStatus() == RouteStatus.DISCARDED));
        assertTrue(ids(dispatch.candidates(accident)).contains(AMBULANCE), "it can be sent again");
    }
}
