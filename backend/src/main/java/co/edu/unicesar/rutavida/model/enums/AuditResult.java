package co.edu.unicesar.rutavida.model.enums;

/** Result of an audited operation (AUDITORIA.resultado). */
public enum AuditResult implements CodedEnum {
    SUCCESS("EXITO"),
    FAILURE("FALLO");

    private final String code;

    AuditResult(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static AuditResult fromCode(String code) {
        return CodedEnum.fromCode(AuditResult.class, code);
    }
}
