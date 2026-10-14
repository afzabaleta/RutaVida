package co.edu.unicesar.rutavida.model.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Phase of the mission of one unit (ASIGNACION_EMERGENCIA.estado).
 * A unit may have only one open assignment (unique index in the database).
 */
public enum AssignmentStatus implements CodedEnum {
    ASSIGNED("ASIGNADA"),
    EN_ROUTE("EN_RUTA"),
    ON_SCENE("EN_SITIO"),
    TRANSPORTING("TRASLADANDO"),
    FINISHED("FINALIZADA"),
    CANCELLED("CANCELADA");

    private final String code;

    AssignmentStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public Set<AssignmentStatus> nextStatuses() {
        return switch (this) {
            case ASSIGNED -> EnumSet.of(EN_ROUTE, CANCELLED);
            case EN_ROUTE -> EnumSet.of(ON_SCENE, CANCELLED);
            case ON_SCENE -> EnumSet.of(TRANSPORTING, FINISHED);
            case TRANSPORTING -> EnumSet.of(FINISHED);
            case FINISHED, CANCELLED -> Collections.emptySet();
        };
    }

    public boolean canChangeTo(AssignmentStatus next) {
        return next != null && nextStatuses().contains(next);
    }

    /** Open = the unit is still busy with this mission. */
    public boolean isOpen() {
        return this != FINISHED && this != CANCELLED;
    }

    public static AssignmentStatus fromCode(String code) {
        return CodedEnum.fromCode(AssignmentStatus.class, code);
    }
}
