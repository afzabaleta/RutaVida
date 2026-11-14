
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.PanicReportView;
import co.edu.unicesar.rutavida.dto.PanicResult;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void oneReportWaitsForOtherPeople() {
        PanicResult result = service.report(press("celular-1", 0), null);

        assertEquals(PanicReportStatus.PENDING, result.status());
        assertNull(result.emergencyId());
        assertEquals(1, result.reportsNearby());
        assertEquals(3, result.reportsNeeded());
        assertTrue(support.city.emergencies.findActive().isEmpty());
    }

    @Test
    void threeDevicesCloseTogetherValidateTheAlertAndTheFastestUnitIsSent() {
        service.report(press("celular-1", 0), null);
        service.report(press("celular-2", 80), null);
        PanicResult result = service.report(press("celular-3", -60), null);

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertEquals("EJEMPLO-AMB-01", result.dispatchedUnit());

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
    void theSameDeviceNeverValidatesAnAlertAndIsStoppedAtTheFourthReport() {
        for (int i = 0; i < 3; i++) {
            assertEquals(PanicReportStatus.PENDING,
                    service.report(press("celular-1", i * 10), null).status());
        }

        BusinessRuleException tooMany = assertThrows(
                BusinessRuleException.class,
                () -> service.report(press("celular-1", 0), null));

        assertTrue(tooMany.getMessage().contains("123"));
        assertTrue(support.city.emergencies.findActive().isEmpty());
    }

    @Test
    void reportsFartherThan300MetersDoNotCount() {
        service.report(press("celular-1", 0), null);
        service.report(press("celular-2", 350), null);
        PanicResult result = service.report(press("celular-3", -250), null);

        assertEquals(PanicReportStatus.PENDING, result.status());
        assertEquals(2, result.reportsNearby(),
                "the third one and the one at the scene; the one 600 m away does not count");
    }

    @Test
    void reportsOlderThanTenMinutesExpireAndDoNotCount() {
        long old1 = support.oldReport("celular-1", 11);
        long old2 = support.oldReport("celular-2", 12);

        PanicResult result = service.report(press("celular-3", 0), null);

        assertEquals(PanicReportStatus.PENDING, result.status());
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
        assertEquals("EJEMPLO-AMB-01", result.dispatchedUnit());
        assertTrue(service.listRecent(null).get(0).byPolice());
    }

    @Test
    void aReportNearAnAlertBeingAttendedJoinsItInsteadOfCreatingAnother() {
        service.report(press("celular-1", 0), null);
        service.report(press("celular-2", 0), null);
        long emergencyId = service.report(press("celular-3", 0), null).emergencyId();

        PanicResult fourth = service.report(press("celular-4", 50), null);

        assertEquals(PanicReportStatus.VALIDATED, fourth.status());
        assertEquals(emergencyId, fourth.emergencyId());
        assertNull(fourth.dispatchedUnit(), "no second unit");
        assertEquals(1, support.city.emergencies.findActive().size());
    }

    @Test
    void withoutAvailableUnitsTheEmergencyWaitsForAManualDispatch() {
        for (long id = 1; id <= 5; id++) {
            support.city.vehicles.updateStatus(id, VehicleOperationalStatus.MAINTENANCE);
        }

        PanicResult result = service.report(
                press("celular-policia", 0), support.sessionOf(POLICE));

        assertEquals(PanicReportStatus.VALIDATED, result.status());
        assertNull(result.dispatchedUnit());

        assertEquals(EmergencyStatus.REPORTED,
                support.city.emergencies.findById(result.emergencyId()).orElseThrow().getStatus());

        assertTrue(support.alerts.all().get(0).getMessage().contains("despache a mano"));
    }

    @Test
    void theDeviceIdIsSavedOnlyAsItsHash() {
        long id = service.report(press("celular-1", 0), null).reportId();

        String saved = support.reports.findById(id).orElseThrow().getDeviceId();

        assertEquals(64, saved.length());
        assertNotEquals("celular-1", saved);
        assertEquals(PanicService.hashDevice("celular-1"), saved);
    }

    @Test
    void aLocationOutsideTheRoadNetworkOrWithoutDeviceIsRejected() {
        assertThrows(ValidationException.class, () -> service.report(
                new PanicAlertRequest("celular-1",
                        new GeoPoint(10.60, -73.00), null, null), null));

        assertThrows(ValidationException.class, () -> service.report(
                new PanicAlertRequest(" ", PanicTestSupport.SCENE, null, null), null));

        assertThrows(ValidationException.class, () -> service.report(
                new PanicAlertRequest("celular-1", PanicTestSupport.SCENE, 99L, null), null));
    }

    @Test
    void discardingAPendingAlertDiscardsTheReportsNearItAndAValidatedOneIsNotDiscarded() {
        long first = service.report(press("celular-1", 0), null).reportId();
        service.report(press("celular-2", 40), null);

        PanicReportView discarded = service.discard(
                first, support.sessionOf(COORDINATOR));

        assertEquals(PanicReportStatus.DISCARDED, discarded.status());
        assertTrue(service.listRecent(PanicReportStatus.PENDING).isEmpty());

        assertEquals(PanicReportStatus.PENDING,
                service.report(press("celular-3", 0), null).status(),
                "the discarded reports do not count");

        long police = service.report(
                press("celular-policia", 0), support.sessionOf(POLICE)).reportId();

        assertThrows(BusinessRuleException.class,
                () -> service.discard(police, support.sessionOf(COORDINATOR)));
    }

    @Test
    void onlyAnActiveUserWithPanicValidateCanValidateAlone() {
        assertTrue(PanicService.canValidateAlone(support.sessionOf(POLICE)));
        assertFalse(PanicService.canValidateAlone(support.sessionOf(COORDINATOR)));
        assertFalse(PanicService.canValidateAlone(null));
        assertFalse(PanicService.canValidateAlone(support.sessionOf(SYSTEM)), "inactive user");
    }

    @Test
    void theEmergencyGoesToTheMiddleOfTheReportsWithTheTypeMostPeopleChose() {
        GeoPoint center = PanicService.center(List.of(
                report(PanicTestSupport.north(0)),
                report(PanicTestSupport.north(100))));

        assertEquals(PanicTestSupport.north(50).latitude(), center.latitude(), 1e-9);

        service.report(new PanicAlertRequest(
                "celular-1", PanicTestSupport.SCENE, 3L, "Choque de dos motos"), null);

        service.report(new PanicAlertRequest(
                "celular-2", PanicTestSupport.SCENE, 3L, null), null);

        long emergencyId = service.report(new PanicAlertRequest(
                "celular-3", PanicTestSupport.SCENE, 1L, null), null).emergencyId();

        Emergency emergency = support.city.emergencies.findById(emergencyId).orElseThrow();

        assertEquals(3L, emergency.getEmergencyTypeId(), "ACCIDENTE_VIAL: 2 of 3");
        assertTrue(emergency.getDescription().contains("Choque de dos motos"));
    }

    private static PanicReport report(GeoPoint where) {
        return new PanicReport(
                1, where, null, "x", null, null, null, PanicReportStatus.PENDING, null);
    }
}
