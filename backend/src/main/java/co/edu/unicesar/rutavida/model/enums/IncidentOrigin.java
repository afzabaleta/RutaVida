package co.edu.unicesar.rutavida.model.enums;

/** Who created a road incident (INCIDENTE_VIAL.origen). */
public enum IncidentOrigin implements CodedEnum {
    OPERATOR("OPERADOR"),
    CITIZEN_QR("QR_CIUDADANO"),
    SIMULATION("SIMULACION");

    private final String code;

    IncidentOrigin(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static IncidentOrigin fromCode(String code) {
        return CodedEnum.fromCode(IncidentOrigin.class, code);
    }
}
