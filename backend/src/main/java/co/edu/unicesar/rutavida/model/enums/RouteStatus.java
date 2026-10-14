package co.edu.unicesar.rutavida.model.enums;

/** Status of a calculated route (RUTA.estado). */
public enum RouteStatus implements CodedEnum {
    PROPOSED("PROPUESTA"),
    ACTIVE("ACTIVA"),
    FINISHED("FINALIZADA"),
    DISCARDED("DESCARTADA");

    private final String code;

    RouteStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static RouteStatus fromCode(String code) {
        return CodedEnum.fromCode(RouteStatus.class, code);
    }
}
