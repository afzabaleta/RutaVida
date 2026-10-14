package co.edu.unicesar.rutavida.model.enums;

/** Administrative status of a road segment (TRAMO_VIAL.estado). */
public enum RoadSegmentStatus implements CodedEnum {
    OPEN("ABIERTO"),
    CLOSED("CERRADO"),
    RESTRICTED("RESTRINGIDO");

    private final String code;

    RoadSegmentStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static RoadSegmentStatus fromCode(String code) {
        return CodedEnum.fromCode(RoadSegmentStatus.class, code);
    }
}
