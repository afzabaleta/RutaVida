package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.PositionSource;
import java.time.LocalDateTime;

/**
 * One recorded position of a unit (table UBICACION_VEHICULO).
 * Only data: the business rules live in the service layer.
 */
public class VehicleLocation {

    private long id;
    private final long vehicleId;
    private final LocalDateTime recordedAt;
    private final GeoPoint location;
    private final Double speedKmh;
    private final Double headingDegrees;
    private final PositionSource source;

    public VehicleLocation(long id,
            long vehicleId,
            LocalDateTime recordedAt,
            GeoPoint location,
            Double speedKmh,
            Double headingDegrees,
            PositionSource source) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.recordedAt = recordedAt;
        this.location = location;
        this.speedKmh = speedKmh;
        this.headingDegrees = headingDegrees;
        this.source = source;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
