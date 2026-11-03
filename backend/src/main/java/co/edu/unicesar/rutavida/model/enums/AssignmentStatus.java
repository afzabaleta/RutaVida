package co.edu.unicesar.rutavida.model.enums;

/**
 * Mission status of an assignment (ASIGNACION_EMERGENCIA.estado).
 * The allowed changes are checked by DispatchService.
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

    public static AssignmentStatus fromCode(String code) {
        return CodedEnum.fromCode(AssignmentStatus.class, code);
    }
}
