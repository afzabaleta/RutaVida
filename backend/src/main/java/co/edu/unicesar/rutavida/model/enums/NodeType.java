package co.edu.unicesar.rutavida.model.enums;

/** Type of road network node (NODO_VIAL.tipo_nodo). */
public enum NodeType implements CodedEnum {
    INTERSECTION("INTERSECCION"),
    ROUNDABOUT("GLORIETA"),
    BRIDGE("PUENTE"),
    ACCESS("ACCESO"),
    WAY_POINT("PUNTO_VIA"),
    OTHER("OTRO");

    private final String code;

    NodeType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static NodeType fromCode(String code) {
        return CodedEnum.fromCode(NodeType.class, code);
    }
}
