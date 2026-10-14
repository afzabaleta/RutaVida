package co.edu.unicesar.rutavida.model.enums;

/** Care level of a hospital (HOSPITAL.nivel). */
public enum HospitalLevel implements CodedEnum {
    LEVEL_I("I"),
    LEVEL_II("II"),
    LEVEL_III("III"),
    LEVEL_IV("IV");

    private final String code;

    HospitalLevel(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static HospitalLevel fromCode(String code) {
        return CodedEnum.fromCode(HospitalLevel.class, code);
    }
}
