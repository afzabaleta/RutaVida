package co.edu.unicesar.rutavida.model.enums;

/**
 * Availability of a unit (VEHICULO_EMERGENCIA.estado_operativo).
 * The phase of the mission is in {@link AssignmentStatus}, not here.
 */
public enum VehicleOperationalStatus implements CodedEnum {
    AVAILABLE("DISPONIBLE"),
    BUSY("OCUPADO"),
    MAINTENANCE("MANTENIMIENTO"),
    OUT_OF_SERVICE("FUERA_SERVICIO");

    private final String code;

    VehicleOperationalStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static VehicleOperationalStatus fromCode(String code) {
        return CodedEnum.fromCode(VehicleOperationalStatus.class, code);
    }
}
