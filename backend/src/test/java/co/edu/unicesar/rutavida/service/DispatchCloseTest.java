package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static co.edu.unicesar.rutavida.service.DispatchTestSupport.AMBULANCE;
import static co.edu.unicesar.rutavida.service.DispatchTestSupport.MEDICAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Closing an emergency (CU-12) on the small city of DispatchTestSupport, without Oracle. What
 * sp_cerrar_emergencia does to the missions, units and routes is tested against Oracle
 * (ProcedureCallIntegrationTest and JdbcEmergencyDAOIntegrationTest).
 */
class DispatchCloseTest {

    private static final LocalDateTime NOW = LocalDateTime.now(EmergencyTestSupport.CLOCK);
    private static final UserSession COORDINATOR = EmergencyTestSupport.session("DESPACHO:GESTIONAR");

    private final DispatchTestSupport city = new DispatchTestSupport();
    private final DispatchService dispatch = city.service();

    /** Dispatches the ambulance to a new medical emergency and returns the assignment id. */
    private long dispatched(long emergency) {
        return dispatch.dispatch(emergency, AMBULANCE, COORDINATOR).assignment().getId();
    }

    @Test
    void aFalseAlarmIsClosedAndAuditedWithTheEmergency() {
        long emergency = city.report(MEDICAL, 1, 2);

        Emergency closed = dispatch.closeEmergency(emergency, COORDINATOR);

        assertEquals(EmergencyStatus.CLOSED, closed.getStatus());
        assertEquals(NOW, closed.getClosedAt(), "the time of the service clock");
        AuditLog audit = city.audit.last();
        assertEquals("EMERGENCIAS", audit.getModule(), "so the history of the emergency shows it");
        assertEquals("CERRAR", audit.getOperation());
        assertEquals(emergency, audit.getRecordId());
        assertEquals("Desde REPORTADA", audit.getDetail());
    }

    @Test
    void anEmergencyIsClosedWhenTheUnitArrived() {
        long emergency = city.report(MEDICAL, 1, 2);
        long mission = dispatched(emergency);
        dispatch.advance(mission, AssignmentStatus.EN_ROUTE);
        dispatch.advance(mission, AssignmentStatus.ON_SCENE);

        Emergency closed = dispatch.closeEmergency(emergency, COORDINATOR);

        assertEquals(EmergencyStatus.CLOSED, closed.getStatus());
        assertEquals("Desde EN_SITIO", city.audit.last().getDetail());
    }

    @Test
    void anEmergencyWithAUnitOnItsWayIsNotClosedNorTwice() {
        long emergency = city.report(MEDICAL, 1, 2);
        long mission = dispatched(emergency);

        BusinessRuleException assigned = assertThrows(BusinessRuleException.class,
                () -> dispatch.closeEmergency(emergency, COORDINATOR));
        assertEquals("No se puede cerrar una emergencia ASIGNADA: primero la unidad debe llegar (EN_SITIO)"
                + " o la misión cancelarse.", assigned.getMessage());
        dispatch.advance(mission, AssignmentStatus.EN_ROUTE);
        assertThrows(BusinessRuleException.class, () -> dispatch.closeEmergency(emergency, COORDINATOR),
                "EN_RUTA");

        long falseAlarm = city.report(MEDICAL, 0, 0);
        dispatch.closeEmergency(falseAlarm, COORDINATOR);
        assertEquals("La emergencia " + falseAlarm + " ya está cerrada.", assertThrows(BusinessRuleException.class,
                () -> dispatch.closeEmergency(falseAlarm, COORDINATOR)).getMessage());
        assertThrows(NotFoundException.class, () -> dispatch.closeEmergency(999, COORDINATOR));
        assertEquals(EmergencyStatus.EN_ROUTE, city.emergencies.findById(emergency).orElseThrow().getStatus(),
                "nothing changed");
    }
}