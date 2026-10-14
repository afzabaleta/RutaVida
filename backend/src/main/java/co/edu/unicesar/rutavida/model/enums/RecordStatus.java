package co.edu.unicesar.rutavida.model.enums;

/** Active/inactive flag of master records (estado CHAR(1) 'A'/'I'). */
public enum RecordStatus implements CodedEnum {
    ACTIVE("A"),
    INACTIVE("I");

    private final String code;

    RecordStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static RecordStatus fromCode(String code) {
        return CodedEnum.fromCode(RecordStatus.class, code);
    }
}
