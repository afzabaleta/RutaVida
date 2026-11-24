
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.PanicReportView;
import co.edu.unicesar.rutavida.dto.PanicResult;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static co.edu.unicesar.rutavida.service.PanicTestSupport.COORDINATOR;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.POLICE;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.SYSTEM;
import static co.edu.unicesar.rutavida.service.PanicTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Panic button rules (CU-22, RN-13 to RN-15) on in-memory DAOs, without Oracle. */
class PanicServiceTest {

    private PanicTestSupport support;
    private PanicService service;

    @BeforeEach
    void setUp() {
        support = new PanicTestSupport();
        service = support.service();
    }

    @Test
    void oneAuthenticatedReportValidatesAndStaysReportedWithoutGps() {
        PanicResult result = service.report(press("celular-1", 0), support.citizen(1));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertTrue(result.createdEmergency());
        assertEquals(1, result.reportsNearby());
        assertEquals(1, result.reportsNeeded());
        assertEquals(EmergencyStatus.REPORTED, support.city.emergencies.findById(result.emergencyId()).orElseThrow().getStatus());
    }

    @Test
    void firstCitizenDispatchesOnlyTheAmbulanceWithLowestRouteEtaAndDuplicatesJoin() {
        addAmbulance("AMB-LEJANA", 0, 0, VehicleOperationalStatus.AVAILABLE, RecordStatus.ACTIVE, true);
        long fastest = addAmbulance("AMB-RAPIDA", 1, 1, VehicleOperationalStatus.AVAILABLE, RecordStatus.ACTIVE, true);
        PanicResult result = service.report(press("celular-1", 0), support.citizen(1));
        PanicResult duplicate = service.report(press("celular-2", 60), support.citizen(2));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertEquals("AMB-RAPIDA", result.dispatchedUnit());
        assertEquals(fastest, result.dispatchedVehicleId());
        assertEquals(result.emergencyId(), duplicate.emergencyId());
        assertFalse(duplicate.createdEmergency());
        assertNull(duplicate.dispatchedVehicleId());
        assertEquals(1, support.city.assignments.findByEmergency(result.emergencyId()).size());

        Emergency emergency = support.city.emergencies.findById(result.emergencyId()).orElseThrow();

        assertEquals(EmergencyStatus.ASSIGNED, emergency.getStatus());
        assertEquals(Severity.HIGH, emergency.getSeverity());
        assertEquals(SYSTEM, emergency.getReportedByUserId());
        assertEquals(1L, emergency.getEmergencyTypeId(), "nobody chose: EMERGENCIA_MEDICA");

        for (PanicReport report : support.reports.findSince(
                emergency.getReportedAt().minusMinutes(1))) {
            assertEquals(PanicReportStatus.VALIDATED, report.getStatus());
            assertEquals(result.emergencyId(), report.getEmergencyId());
        }

        assertEquals(1, support.alerts.all().size(), "one alert to the only active coordinator");
        assertEquals(COORDINATOR, support.alerts.all().get(0).getUserId());
    }

    @Test
    void theFourthReportIsRejectedAcrossDevicesForTheSameUser() {
        for (int i = 0; i < 3; i++) {
            PanicResult result = service.report(press("otro-celular-" + i, i * 10), support.citizen(1));

            assertEquals(PanicReportStatus.VALIDATED, result.status());
            assertEquals(i == 0, result.createdEmergency());
        }

        BusinessRuleException tooMany = assertThrows(
                BusinessRuleException.class,
                () -> service.report(press("celular-1", 0), support.citizen(1)));

        assertTrue(tooMany.getMessage().contains("123"));
        assertEquals(1, support.city.emergencies.findActive().size());
    }

    @Test
    void nobodyPressesTheButtonWithoutAnAccount() {
        assertThrows(ValidationException.class, () -> service.report(press("celular-1", 0), null));
        assertTrue(support.reports.findSince(java.time.LocalDateTime.MIN).isEmpty(), "nothing is saved");
    }

    @Test
    void incidentsFartherThan300MetersAreIndependent() {
        PanicResult first = service.report(press("celular-1", 0), support.citizen(1));
        service.report(press("celular-2", 350), support.citizen(2));
        PanicResult result = service.report(press("celular-3", -250), support.citizen(3));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertEquals(first.emergencyId(), result.emergencyId());
        assertEquals(2, support.city.emergencies.findActive().size());
    }

    @Test
    void reportsOlderThanTenMinutesExpireAndDoNotCount() {
        long old1 = support.oldReport(1, 11);
        long old2 = support.oldReport(2, 12);

        PanicResult result = service.report(press("celular-3", 0), support.citizen(3));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertEquals(PanicReportStatus.EXPIRED,
                support.reports.findById(old1).orElseThrow().getStatus());
        assertEquals(PanicReportStatus.EXPIRED,
                support.reports.findById(old2).orElseThrow().getStatus());
    }

    @Test
    void aPoliceOfficerValidatesTheAlertAlone() {
        PanicResult result = service.report(
                press("celular-policia", 0), support.sessionOf(POLICE));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertNull(result.dispatchedUnit());
        assertTrue(service.listRecent(null).get(0).byPolice());
    }

    @Test
    void aReportNearAnAlertBeingAttendedJoinsItInsteadOfCreatingAnother() {
        service.report(press("celular-1", 0), support.citizen(1));
        service.report(press("celular-2", 0), support.citizen(2));
        long emergencyId = service.report(press("celular-3", 0), support.citizen(3)).emergencyId();

        PanicResult fourth = service.report(press("celular-4", 50), support.citizen(4));

        assertEquals(PanicReportStatus.VALIDATED, fourth.status());
        assertEquals(emergencyId, fourth.emergencyId());
        assertNull(fourth.dispatchedUnit(), "no second unit");
        assertEquals(1, support.city.emergencies.findActive().size());
    }

    @Test
    void withoutAvailableUnitsTheEmergencyWaitsForAManualDispatch() {
        support.ambulance("AMB-SIN-CONDUCTOR", PanicTestSupport.SCENE, VehicleOperationalStatus.AVAILABLE);
        addAmbulance("AMB-GPS-VENCIDO", 1, 1, VehicleOperationalStatus.AVAILABLE, RecordStatus.ACTIVE, true);
        support.gpsClock.advance(Duration.ofSeconds(16));
        addAmbulance("AMB-OCUPADA", 1, 1, VehicleOperationalStatus.BUSY, RecordStatus.ACTIVE, true);
        addAmbulance("AMB-MANTENIMIENTO", 1, 1, VehicleOperationalStatus.MAINTENANCE, RecordStatus.ACTIVE, true);
        addAmbulance("AMB-CONDUCTOR-INACTIVO", 1, 1, VehicleOperationalStatus.AVAILABLE, RecordStatus.INACTIVE, true);
        addAmbulance("AMB-SIN-SENAL", 1, 1, VehicleOperationalStatus.AVAILABLE, RecordStatus.ACTIVE, false);
        addAmbulance("EJEMPLO-AMB-GPS", 1, 1, VehicleOperationalStatus.AVAILABLE, RecordStatus.ACTIVE, true);

        PanicResult result = service.report(
                press("celular-policia", 0), support.sessionOf(POLICE));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertNull(result.dispatchedUnit());

        assertEquals(EmergencyStatus.REPORTED,
                support.city.emergencies.findById(result.emergencyId()).orElseThrow().getStatus());

        assertTrue(support.alerts.all().get(0).getMessage().contains("GPS activo"));
    }

    @Test
    void simultaneousReportsCannotDispatchTheSameAmbulanceTwice() throws Exception {
        long vehicle = addAmbulance("AMB-UNICA", 1, 1, VehicleOperationalStatus.AVAILABLE, RecordStatus.ACTIVE, true);
        var first = support.citizen(1);
        var second = support.citizen(2);
        var start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.report(press("concurrente-a", 0), first);
            });
            var b = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.report(new PanicAlertRequest("concurrente-b",
                        DispatchTestSupport.at(0, 0), null, null), second);
            });
            PanicResult left = a.get(10, TimeUnit.SECONDS);
            PanicResult right = b.get(10, TimeUnit.SECONDS);
            assertNotEquals(left.emergencyId(), right.emergencyId());
            assertTrue((left.dispatchedVehicleId() == null) ^ (right.dispatchedVehicleId() == null));
            assertEquals(vehicle, support.city.assignments.findOpenByVehicle(vehicle).orElseThrow().getVehicleId());
        }
    }

    @Test
    void theDeviceIdIsSavedOnlyAsItsHashAndTheReportKeepsWhoSentIt() {
        long id = service.report(press("celular-1", 0), support.citizen(1)).reportId();

        String saved = support.reports.findById(id).orElseThrow().getDeviceId();

        assertEquals(64, saved.length());
        assertNotEquals("celular-1", saved);
        assertEquals(PanicService.hashDevice("celular-1"), saved);
        assertEquals(support.citizen(1).user().getId(),
                support.reports.findById(id).orElseThrow().getUserId(), "traceability: who pressed it");
    }

    @Test
    void aLocationOutsideTheRoadNetworkOrWithoutDeviceIsRejected() {
        assertThrows(ValidationException.class, () -> service.report(
                new PanicAlertRequest("celular-1",
                        new GeoPoint(10.60, -73.00), null, null), support.citizen(1)));

        assertThrows(ValidationException.class, () -> service.report(
                new PanicAlertRequest(" ", PanicTestSupport.SCENE, null, null), support.citizen(1)));

        assertThrows(ValidationException.class, () -> service.report(
                new PanicAlertRequest("celular-1", PanicTestSupport.SCENE, 99L, null), support.citizen(1)));
    }

    @Test
    void discardingAPendingAlertDiscardsTheReportsNearItAndAValidatedOneIsNotDiscarded() {
        long first = support.oldReport(1, 1);
        support.oldReport(2, 1);

        PanicReportView discarded = service.discard(
                first, support.sessionOf(COORDINATOR));

        assertEquals(PanicReportStatus.DISCARDED, discarded.status());
        assertTrue(service.listRecent(PanicReportStatus.PENDING).isEmpty());

        assertEquals(PanicReportStatus.VALIDATED,
                service.report(press("celular-3", 0), support.citizen(3)).status(),
                "the discarded reports do not count");

        long police = service.report(
                press("celular-policia", 0), support.sessionOf(POLICE)).reportId();

        assertThrows(BusinessRuleException.class,
                () -> service.discard(police, support.sessionOf(COORDINATOR)));
    }

    @Test
    void aCitizenReportIsNotLabeledAsAPoliceReport() {
        service.report(press("celular-1", 0), support.citizen(1));
        assertFalse(service.listRecent(null).getFirst().byPolice());
    }

    @Test
    void reportUsesItsLocationAndKeepsSeparateIncidentTypes() {
        PanicResult first = service.report(new PanicAlertRequest(
                "celular-1", PanicTestSupport.SCENE, 3L, "Choque de dos motos"), support.citizen(1));

        service.report(new PanicAlertRequest(
                "celular-2", PanicTestSupport.SCENE, 3L, null), support.citizen(2));

        long emergencyId = service.report(new PanicAlertRequest(
                "celular-3", PanicTestSupport.SCENE, 1L, null), support.citizen(3)).emergencyId();

        Emergency emergency = support.city.emergencies.findById(first.emergencyId()).orElseThrow();

        assertNotEquals(emergencyId, emergency.getId());
        assertEquals(3L, emergency.getEmergencyTypeId());
        assertEquals(PanicTestSupport.SCENE, emergency.getLocation());
        assertTrue(emergency.getDescription().contains("Choque de dos motos"));
    }

    // ------------------------------------------------------------------ RN-16: false alarms

    @Test
    void aCitizenWithTwoDiscardedAlertsIsBlockedAndItsPhonesAreLoggedOut() {
        UserSession joker = support.citizen(1);
        String phone = support.citizenService().startSession(joker).token();

        var first = service.report(press("celular-1", 0), joker);
        support.city.service().closeEmergency(first.emergencyId(), support.sessionOf(COORDINATOR));
        service.discard(first.reportId(), support.sessionOf(COORDINATOR));

        assertEquals(RecordStatus.ACTIVE, support.users.findById(joker.user().getId()).orElseThrow().getStatus(),
                "one false alarm is a warning, not a block");
        assertTrue(support.citizenService().findSession(phone).isPresent());

        var second = service.report(press("celular-1", 0), joker);
        support.city.service().closeEmergency(second.emergencyId(), support.sessionOf(COORDINATOR));
        service.discard(second.reportId(), support.sessionOf(COORDINATOR));

        assertEquals(RecordStatus.INACTIVE, support.users.findById(joker.user().getId()).orElseThrow().getStatus());
        assertTrue(support.citizenService().findSession(phone).isEmpty(), "the phone is logged out");
        assertTrue(support.alerts.all().stream().anyMatch(alert ->
                alert.getUserId() == COORDINATOR && alert.getMessage().contains("ciudadano1")
                        && alert.getMessage().contains("bloqueó")), "the coordinators are told");
        assertTrue(support.city.audit.findByRecord("USUARIOS", joker.user().getId()).stream()
                .anyMatch(log -> "BLOQUEAR".equals(log.getOperation())));
    }

    @Test
    void everyPersonOfADiscardedAlertGetsItsFalseAlarmButOnlyRepeatersAreBlocked() {
        UserSession repeater = support.citizen(1);
        UserSession firstTimer = support.citizen(2);

        service.discard(support.oldReport(1, 1),
                support.sessionOf(COORDINATOR));

        long together = support.oldReport(1, 1);
        support.oldReport(2, 1);
        service.discard(together, support.sessionOf(COORDINATOR));

        assertEquals(RecordStatus.INACTIVE, support.users.findById(repeater.user().getId()).orElseThrow().getStatus());
        assertEquals(RecordStatus.ACTIVE, support.users.findById(firstTimer.user().getId()).orElseThrow().getStatus(),
                "its first false alarm");
    }

    @Test
    void falseAlarmsOlderThan30DaysDoNotBlock() {
        UserSession citizen = support.citizen(1);
        support.reports.insert(new PanicReport(0, PanicTestSupport.SCENE,
                java.time.LocalDateTime.now(EmergencyTestSupport.CLOCK).minusDays(31), "x",
                citizen.user().getId(), null, null, PanicReportStatus.DISCARDED, null));

        service.discard(support.oldReport(1, 1),
                support.sessionOf(COORDINATOR));

        assertEquals(RecordStatus.ACTIVE, support.users.findById(citizen.user().getId()).orElseThrow().getStatus());
    }

    private long addAmbulance(String code, int row, int column,
                              VehicleOperationalStatus status, RecordStatus driverStatus, boolean transmit) {
        GeoPoint location = DispatchTestSupport.at(row, column);
        long vehicleId = support.ambulance(code, location, status);
        var driver = support.bindDriver(code.toLowerCase(java.util.Locale.ROOT), vehicleId, driverStatus);
        if (transmit) support.transmit(driver, location);
        return vehicleId;
    }
}
