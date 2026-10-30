package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import java.time.LocalDateTime;

/**
 * An ambulance or fire truck with its status and last known position (table VEHICULO_EMERGENCIA).
 * Only data: the business rules live in the service layer.
 */
public class EmergencyVehicle {

    private long id;
    private final long vehicleTypeId;
    private final long stationId;
    private final String unitCode;
    private final String plate;
    private VehicleOperationalStatus operationalStatus;
    private GeoPoint lastLocation;
    private LocalDateTime lastLocationAt;

    public EmergencyVehicle(long id,
            long vehicleTypeId,
            long stationId,
            String unitCode,
            String plate,
            VehicleOperationalStatus operationalStatus,
            GeoPoint lastLocation,
            LocalDateTime lastLocationAt) {
        this.id = id;
        this.vehicleTypeId = vehicleTypeId;
        this.stationId = stationId;
        this.unitCode = unitCode;
        this.plate = plate;
        this.operationalStatus = operationalStatus;
        this.lastLocation = lastLocation;
        this.lastLocationAt = lastLocationAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getVehicleTypeId() {
        return vehicleTypeId;
    }

    public long getStationId() {
        return stationId;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public String getPlate() {
        return plate;
    }

    public VehicleOperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(VehicleOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public GeoPoint getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(GeoPoint lastLocation) {
        this.lastLocation = lastLocation;
    }

    public LocalDateTime getLastLocationAt() {
        return lastLocationAt;
    }

    public void setLastLocationAt(LocalDateTime lastLocationAt) {
        this.lastLocationAt = lastLocationAt;
    }
}
