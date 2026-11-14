package co.edu.unicesar.rutavida.model.enums;

/** Status of a panic button report (REPORTE_PANICO.estado, CU-22). */
public enum PanicReportStatus implements CodedEnum {
    /** Waiting for more reports nearby (or for a police report) to validate the alert. */
    PENDING("PENDIENTE"),
    /** It was part of a validated alert: it is linked to the emergency it created. */
    VALIDATED("VALIDADO"),
    /** A coordinator marked the alert as false. */
    DISCARDED("DESCARTADO"),
    /** Nobody else reported nearby in time. */
    EXPIRED("VENCIDO");

    private final String code;

    PanicReportStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static PanicReportStatus fromCode(String code) {
        return CodedEnum.fromCode(PanicReportStatus.class, code);
    }
}
