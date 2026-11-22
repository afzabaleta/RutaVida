package co.edu.unicesar.rutavida.model.enums;

/**
 * Life cycle of an emergency (EMERGENCIA.estado).
 * The allowed changes are checked by EmergencyService.
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

    public static EmergencyStatus fromCode(String code) {
        return CodedEnum.fromCode(EmergencyStatus.class, code);
    }
}
