package co.edu.unicesar.rutavida.model.enums;

/** Main or alternative route (RUTA.tipo_ruta). */
public enum RouteType implements CodedEnum {
    MAIN("PRINCIPAL"),
    ALTERNATIVE("ALTERNATIVA");

    private final String code;

    RouteType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static RouteType fromCode(String code) {
        return CodedEnum.fromCode(RouteType.class, code);
    }
}
