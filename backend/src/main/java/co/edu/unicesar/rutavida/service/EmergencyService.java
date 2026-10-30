package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Business rules of an emergency: what a new emergency needs and which status
 * changes are allowed. Registering it in Oracle (CU-06) is added with the EmergencyDAO.
 */
public class EmergencyService {

    /** Allowed changes: REPORTADA → ASIGNADA → EN_RUTA → EN_SITIO → TRASLADANDO → CERRADA. */
    private static final Map<EmergencyStatus, Set<EmergencyStatus>> NEXT = Map.of(
            EmergencyStatus.REPORTED, EnumSet.of(EmergencyStatus.ASSIGNED, EmergencyStatus.CLOSED),
            EmergencyStatus.ASSIGNED, EnumSet.of(EmergencyStatus.EN_ROUTE, EmergencyStatus.REPORTED),
            EmergencyStatus.EN_ROUTE, EnumSet.of(EmergencyStatus.ON_SCENE, EmergencyStatus.REPORTED),
            EmergencyStatus.ON_SCENE, EnumSet.of(EmergencyStatus.TRANSPORTING, EmergencyStatus.CLOSED),
            EmergencyStatus.TRANSPORTING, EnumSet.of(EmergencyStatus.CLOSED),
            EmergencyStatus.CLOSED, EnumSet.noneOf(EmergencyStatus.class));

    /** Checks the data of an emergency before saving it (CU-06). */
    public void validateNew(Emergency emergency) {
        ValidationUtil.requireNonNull(emergency, "emergencia");
        ValidationUtil.requirePositiveId(emergency.getEmergencyTypeId(), "tipo de emergencia");
        ValidationUtil.requirePositiveId(emergency.getReportedByUserId(), "usuario que reporta");
        ValidationUtil.requireText(emergency.getAddressReference(), "dirección de referencia", 200);
        ValidationUtil.requireLocation(emergency.getLocation());
        ValidationUtil.requireNonNull(emergency.getReportedAt(), "fecha de reporte");
        ValidationUtil.requireNonNull(emergency.getSeverity(), "gravedad");
        ValidationUtil.optionalText(emergency.getDescription(), "descripción", 400);
        if (emergency.getStatus() != EmergencyStatus.REPORTED) {
            throw new ValidationException("Una emergencia nueva debe iniciar en estado REPORTADA.");
        }
    }

    public boolean canChange(EmergencyStatus from, EmergencyStatus to) {
        return from != null && to != null && NEXT.get(from).contains(to);
    }

    /** Changes the status; CERRADA also records the closing time. */
    public void changeStatus(Emergency emergency, EmergencyStatus next, LocalDateTime at) {
        ValidationUtil.requireNonNull(next, "nuevo estado");
        ValidationUtil.requireNonNull(at, "fecha del cambio");
        if (!canChange(emergency.getStatus(), next)) {
            throw new BusinessRuleException("No se puede cambiar la emergencia de "
                    + emergency.getStatus().code() + " a " + next.code() + ".");
        }
        if (next == EmergencyStatus.CLOSED && at.isBefore(emergency.getReportedAt())) {
            throw new ValidationException("La fecha de cierre no puede ser anterior al reporte.");
        }
        emergency.setStatus(next);
        emergency.setClosedAt(next == EmergencyStatus.CLOSED ? at : null);
    }

    /** An emergency is active until it is closed. */
    public boolean isActive(Emergency emergency) {
        return emergency.getStatus() != EmergencyStatus.CLOSED;
    }
}
