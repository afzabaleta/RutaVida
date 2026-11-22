package co.edu.unicesar.rutavida.model.enums;

/** Origin of a traffic reading (TRAFICO.fuente). Simulated data must never be shown as real. */
public enum TrafficSource implements CodedEnum {
    MANUAL("MANUAL"),
    SIMULATION("SIMULACION"),
    SENSOR("SENSOR"),
    EXTERNAL_API("API_EXTERNA");

    private final String code;

    TrafficSource(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static TrafficSource fromCode(String code) {
        return CodedEnum.fromCode(TrafficSource.class, code);
    }
}
