package co.edu.unicesar.rutavida.model.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Life cycle of an emergency (EMERGENCIA.estado).
 *
 * <p>GRASP Information Expert: the status itself knows which statuses may follow it,
 * so no service needs a chain of if/else to validate a change.
 *
 * <ul>
 *   <li>REPORTED → ASSIGNED, or CLOSED (false alarm)</li>
 *   <li>ASSIGNED → EN_ROUTE, or back to REPORTED (assignment cancelled)</li>
 *   <li>EN_ROUTE → ON_SCENE, or back to REPORTED (unit replaced)</li>
 *   <li>ON_SCENE → TRANSPORTING, or CLOSED (no transfer needed)</li>
 *   <li>TRANSPORTING → CLOSED</li>
 * </ul>
 */
public enum EmergencyStatus implements CodedEnum {
    REPORTED("REPORTADA"),
    ASSIGNED("ASIGNADA"),
    EN_ROUTE("EN_RUTA"),
    ON_SCENE("EN_SITIO"),
    TRANSPORTING("TRASLADANDO"),
    CLOSED("CERRADA");

    private final String code;

    EmergencyStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    /** Statuses that may follow this one. */
    public Set<EmergencyStatus> nextStatuses() {
        return switch (this) {
            case REPORTED -> EnumSet.of(ASSIGNED, CLOSED);
            case ASSIGNED -> EnumSet.of(EN_ROUTE, REPORTED);
            case EN_ROUTE -> EnumSet.of(ON_SCENE, REPORTED);
            case ON_SCENE -> EnumSet.of(TRANSPORTING, CLOSED);
            case TRANSPORTING -> EnumSet.of(CLOSED);
            case CLOSED -> Collections.emptySet();
        };
    }

    public boolean canChangeTo(EmergencyStatus next) {
        return next != null && nextStatuses().contains(next);
    }

    /** An emergency is active until it is closed. */
    public boolean isActive() {
        return this != CLOSED;
    }

    public static EmergencyStatus fromCode(String code) {
        return CodedEnum.fromCode(EmergencyStatus.class, code);
    }
}
