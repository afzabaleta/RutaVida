package co.edu.unicesar.rutavida.model.enums;

/** Traffic level of a road segment (TRAFICO.nivel). */
public enum TrafficLevel implements CodedEnum {
    FLUID("FLUIDO"),
    MODERATE("MODERADO"),
    CONGESTED("CONGESTIONADO");

    private final String code;

    TrafficLevel(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static TrafficLevel fromCode(String code) {
        return CodedEnum.fromCode(TrafficLevel.class, code);
    }
}
