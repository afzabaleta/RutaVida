package co.edu.unicesar.rutavida.model.enums;

/** Current light of a traffic signal (SEMAFORO.estado_actual). */
public enum TrafficLightState implements CodedEnum {
    GREEN("VERDE"),
    YELLOW("AMARILLO"),
    RED("ROJO"),
    OUT_OF_SERVICE("FUERA_SERVICIO");

    private final String code;

    TrafficLightState(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static TrafficLightState fromCode(String code) {
        return CodedEnum.fromCode(TrafficLightState.class, code);
    }
}
