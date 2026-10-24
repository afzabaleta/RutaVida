
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;

/**
 * One recorded position of a unit (UBICACION_VEHICULO).
 * It is history: nothing changes after creation, except assignId.
 *
 * In the first version, positions come from the simulator and
 * must be identified as simulated rather than real GPS data.
 */
public class VehicleLocation {

    private long id;
    private final long vehicleId;
    private final LocalDateTime recordedAt;
    private final GeoPoint location;
    private final Double speedKmh;
    private final Double headingDegrees;
    private final PositionSource source;

    public VehicleLocation(long id, long vehicleId,
                           LocalDateTime recordedAt,
                           GeoPoint location, Double speedKmh,
                           Double headingDegrees, PositionSource source) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        if (speedKmh != null) {
            ValidationUtil.requireInRange(
                    speedKmh, 0, 99_999.99, "velocidad");
        }

        if (headingDegrees != null
                && !(headingDegrees >= 0 && headingDegrees < 360)) {
            throw new ValidationException(
                    "La dirección debe estar entre 0 y menos de 360 grados.");
        }

        this.id = id;
        this.vehicleId = ValidationUtil.requirePositiveId(
                vehicleId, "unidad");
        this.recordedAt = ValidationUtil.requireNonNull(
                recordedAt, "fecha de la posición");
        this.location = ValidationUtil.requireNonNull(
                location, "ubicación");
        this.speedKmh = speedKmh;
        this.headingDegrees = headingDegrees;
        this.source = ValidationUtil.requireNonNull(
                source, "fuente");
    }

    /** Assigns an identifier only to an unsaved position. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    /** Identifies positions originating from the simulator. */
    public boolean isSimulated() {
        return source == PositionSource.SIMULATION;
    }

    public long getId() {
        return id;
    }

    public long getVehicleId() {
        return vehicleId;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public Double getHeadingDegrees() {
        return headingDegrees;
    }

    public PositionSource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof VehicleLocation that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "VehicleLocation{id=" + id
                + ", vehicleId=" + vehicleId
                + ", recordedAt=" + recordedAt
                + ", source=" + source + "}";
    }
}
