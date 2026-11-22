package co.edu.unicesar.rutavida.model.enums;

/** Type of road incident (INCIDENTE_VIAL.tipo_incidente). */
public enum IncidentType implements CodedEnum {
    ACCIDENT("ACCIDENTE"),
    ROADWORK("OBRA"),
    CLOSURE("CIERRE"),
    PROTEST("MANIFESTACION"),
    OTHER("OTRO");

    private final String code;

    IncidentType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static IncidentType fromCode(String code) {
        return CodedEnum.fromCode(IncidentType.class, code);
    }
}
