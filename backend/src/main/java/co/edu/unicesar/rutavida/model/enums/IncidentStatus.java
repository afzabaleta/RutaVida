package co.edu.unicesar.rutavida.model.enums;

/**
 * Status of a road incident (INCIDENTE_VIAL.estado).
 * New incidents start as PENDING and only ACTIVE ones affect route calculation.
 */
public enum IncidentStatus implements CodedEnum {
    PENDING("PENDIENTE"),
    ACTIVE("ACTIVO"),
    CLOSED("CERRADO"),
    REJECTED("RECHAZADO");

    private final String code;

    IncidentStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    /** Only validated, still-open incidents change the cost of a road segment. */
    public boolean affectsRouting() {
        return this == ACTIVE;
    }

    public static IncidentStatus fromCode(String code) {
        return CodedEnum.fromCode(IncidentStatus.class, code);
    }
}
