package co.edu.unicesar.rutavida.model.enums;

/** Origin of a vehicle position (UBICACION_VEHICULO.fuente). Simulated data must never be shown as real. */
public enum PositionSource implements CodedEnum {
    SIMULATION("SIMULACION"),
    GPS("GPS");

    private final String code;

    PositionSource(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static PositionSource fromCode(String code) {
        return CodedEnum.fromCode(PositionSource.class, code);
    }
}
