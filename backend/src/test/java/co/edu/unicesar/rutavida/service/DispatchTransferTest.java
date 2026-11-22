package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.HospitalOption;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.unicesar.rutavida.service.DispatchTestSupport.ACCIDENT;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.CONFIRMED_HOSPITAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.FAR_HOSPITAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.FIRE_TRUCK;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.INACTIVE_HOSPITAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.MEDICAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.NEAR_UNCONFIRMED_HOSPITAL;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.NO_EMERGENCY_ROOM_HOSPITAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transfer to a hospital (CU-11, RN-10, K10) on the small city of DispatchTestSupport,
 * without Oracle: the ambulance at (1,1) attends an emergency at (1,2).
 */
class DispatchTransferTest {

    private static final UserSession COORDINATOR = EmergencyTestSupport.session("DESPACHO:GESTIONAR");

    private final DispatchTestSupport city = new DispatchTestSupport();
    private final DispatchService dispatch = city.service();

    /** A mission of {@code vehicleId} to an emergency at (1,2), already EN_SITIO. */
    private long missionOnScene(long vehicleId) {
        long emergency = city.report(vehicleId == FIRE_TRUCK ? ACCIDENT : MEDICAL, 1, 2);
        long mission = dispatch.dispatch(emergency, vehicleId, COORDINATOR).assignment().getId();
        dispatch.advance(mission, AssignmentStatus.EN_ROUTE);
        dispatch.advance(mission, AssignmentStatus.ON_SCENE);
        return mission;
    }

    private List<Route> routesTo(long hospitalId) {
        return city.routes.all().stream()
                .filter(route -> route.getDestinationHospitalId() != null && route.getDestinationHospitalId() == hospitalId)
                .toList();
    }

    @Test
    void theHospitalsAreRankedByEtaAndAConfirmedService() {
        List<HospitalOption> options = dispatch.hospitalsFor(missionOnScene(AMBULANCE));

        assertEquals(List.of(CONFIRMED_HOSPITAL, NEAR_UNCONFIRMED_HOSPITAL),
                options.stream().map(HospitalOption::hospitalId).toList(),
                "not the one without URGENCIAS, the inactive one nor the one outside the road network");
        HospitalOption confirmed = options.get(0);
        HospitalOption near = options.get(1);
        assertTrue(near.etaMinutes() < confirmed.etaMinutes(), "the unconfirmed one is closer...");
        assertFalse(near.serviceConfirmed());
        assertEquals(near.etaMinutes() + DispatchService.UNCONFIRMED_PENALTY_MINUTES, near.scoreMinutes(), 1e-9,
                "...but it pays the penalty of RN-10");
        assertTrue(confirmed.serviceConfirmed());
        assertEquals(confirmed.etaMinutes(), confirmed.scoreMinutes(), 1e-9);
        assertEquals(0.44, confirmed.distanceKm(), 0.01, "2 streets of 220 m");
        assertEquals("II", confirmed.level());
        assertEquals(DispatchTestSupport.at(0, 0), confirmed.location());
    }

    @Test
    void onlyAnAmbulanceOnSceneChoosesAHospital() {
        long emergency = city.report(MEDICAL, 1, 2);
        long onItsWay = dispatch.dispatch(emergency, AMBULANCE, COORDINATOR).assignment().getId();
        assertThrows(BusinessRuleException.class, () -> dispatch.hospitalsFor(onItsWay), "ASIGNADA, not EN_SITIO");

        long fireTruck = missionOnScene(FIRE_TRUCK);
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> dispatch.hospitalsFor(fireTruck));
        assertTrue(error.getMessage().contains("Solo una ambulancia"), error.getMessage());
        assertThrows(BusinessRuleException.class,
                () -> dispatch.startTransfer(fireTruck, CONFIRMED_HOSPITAL, COORDINATOR));
        assertThrows(NotFoundException.class, () -> dispatch.hospitalsFor(999));
    }

    @Test
    void theTransferSavesTheRoutesToTheHospitalAndMovesTheMission() {
        long mission = missionOnScene(AMBULANCE);
        long emergency = city.assignments.findById(mission).orElseThrow().getEmergencyId();

        DispatchResult transfer = dispatch.startTransfer(mission, CONFIRMED_HOSPITAL, COORDINATOR);

        assertEquals(AssignmentStatus.TRANSPORTING, transfer.assignment().getStatus());
        assertEquals(EmergencyStatus.TRANSPORTING, city.emergencies.findById(emergency).orElseThrow().getStatus());
        List<Route> toHospital = routesTo(CONFIRMED_HOSPITAL);
        assertEquals(transfer.routes().size(), toHospital.size(), "every route of the transfer goes to the hospital");
        assertEquals(RouteStatus.ACTIVE, toHospital.get(0).getStatus(), "the recommended one, the one it drives");
        assertTrue(toHospital.stream().skip(1).allMatch(route -> route.getStatus() == RouteStatus.PROPOSED));
        assertEquals(toHospital.stream().map(Route::getId).toList(),
                dispatch.missionRoutes(mission).stream().map(Route::getId).toList(), "the web reads them with 6.4");
        AuditLog audit = city.audit.last();
        assertEquals("TRASLADAR", audit.getOperation());
        assertEquals(Long.valueOf(mission), audit.getRecordId());
        assertTrue(audit.getDetail().contains("PRUEBA HOSPITAL CONFIRMADO"), audit.getDetail());
    }

    @Test
    void aHospitalThatCannotReceiveThePatientIsRejected() {
        long mission = missionOnScene(AMBULANCE);

        for (long hospital : List.of(NO_EMERGENCY_ROOM_HOSPITAL, INACTIVE_HOSPITAL, FAR_HOSPITAL)) {
            assertThrows(BusinessRuleException.class, () -> dispatch.startTransfer(mission, hospital, COORDINATOR),
                    "hospital " + hospital);
        }
        assertThrows(NotFoundException.class, () -> dispatch.startTransfer(mission, 999, COORDINATOR));
        assertThrows(ValidationException.class, () -> dispatch.startTransfer(mission, 0, COORDINATOR));
        assertThrows(ValidationException.class, () -> dispatch.advance(mission, AssignmentStatus.TRANSPORTING),
                "TRASLADANDO always goes with its hospital");
        assertEquals(AssignmentStatus.ON_SCENE, city.assignments.findById(mission).orElseThrow().getStatus(),
                "nothing changed");
        assertTrue(city.routes.all().stream().allMatch(route -> route.getDestinationHospitalId() == null));
    }

    @Test
    void finishingAtTheHospitalEndsItsRoutesAndFreesTheAmbulance() {
        long mission = missionOnScene(AMBULANCE);
        dispatch.startTransfer(mission, CONFIRMED_HOSPITAL, COORDINATOR);

        dispatch.advance(mission, AssignmentStatus.FINISHED);

        List<Route> toHospital = routesTo(CONFIRMED_HOSPITAL);
        assertEquals(RouteStatus.FINISHED, toHospital.get(0).getStatus(), "the route it drove");
        assertTrue(toHospital.stream().skip(1).allMatch(route -> route.getStatus() == RouteStatus.DISCARDED));
        assertTrue(dispatch.missionRoutes(mission).isEmpty());
        assertEquals(VehicleOperationalStatus.AVAILABLE,
                city.vehicles.findById(AMBULANCE).orElseThrow().getOperationalStatus());
    }
}