package co.edu.unicesar.rutavida.model.enums;

/** Type of operational alert (ALERTA.tipo_alerta). */
public enum AlertType implements CodedEnum {
    CONGESTION("CONGESTION"),
    ACCIDENT_ON_ROUTE("ACCIDENTE_EN_RUTA"),
    ROAD_CLOSURE("CIERRE_VIA"),
    ROUTE_RECALCULATION("RECALCULO_RUTA"),
    AVAILABILITY_CHANGE("CAMBIO_DISPONIBILIDAD"),
    OTHER("OTRO");

    private final String code;

    AlertType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static AlertType fromCode(String code) {
        return CodedEnum.fromCode(AlertType.class, code);
    }
}
