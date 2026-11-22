package co.edu.unicesar.rutavida.model.enums;

/** Severity of an emergency or a road incident (one catalog for both tables). */
public enum Severity implements CodedEnum {
    LOW("BAJA"),
    MEDIUM("MEDIA"),
    HIGH("ALTA"),
    CRITICAL("CRITICA");

    private final String code;

    Severity(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static Severity fromCode(String code) {
        return CodedEnum.fromCode(Severity.class, code);
    }
}
