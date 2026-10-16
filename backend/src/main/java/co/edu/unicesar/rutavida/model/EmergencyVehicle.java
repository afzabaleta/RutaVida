
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.Locale;

/** An emergency response vehicle with an operational status and location. */
public class EmergencyVehicle implements Locatable {

    private long id;
    private final long vehicleTypeId;
    private final long stationId;
    private final String unitCode;
    private final String plate;

    private VehicleOperationalStatus operationalStatus;
    private GeoPoint lastLocation;
    private LocalDateTime lastLocationAt;

    public EmergencyVehicle(long id, long vehicleTypeId,
                            long stationId, String unitCode,
                            String plate,
                            VehicleOperationalStatus operationalStatus,
                            GeoPoint lastLocation,
                            LocalDateTime lastLocationAt) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.vehicleTypeId = ValidationUtil.requirePositiveId(
                vehicleTypeId, "tipo de vehículo");
        this.stationId = ValidationUtil.requirePositiveId(
                stationId, "estación");

        this.unitCode = ValidationUtil.requireText(
                        unitCode, "código de unidad", 30)
                .toUpperCase(Locale.ROOT);

        String validatedPlate = ValidationUtil.optionalText(
                plate, "placa", 10);

        this.plate = validatedPlate == null
                ? null
                : validatedPlate.replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);

        this.operationalStatus = ValidationUtil.requireNonNull(
                operationalStatus, "estado operativo");

        if (lastLocation != null && lastLocationAt == null) {
            throw new ValidationException(
                    "La fecha de ubicación es obligatoria cuando existe una posición.");
        }

        this.lastLocation = lastLocation;
        this.lastLocationAt = lastLocationAt;
    }

    /** Assigns an identifier only to an unsaved vehicle. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    public long getId() {
        return id;
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

    @Override
    public GeoPoint getLocation() {
        return lastLocation;
    }

    public LocalDateTime getLastLocationAt() {
        return lastLocationAt;
    }

    /** Returns true when the vehicle can be dispatched. */
    public boolean isDispatchable() {
        return operationalStatus.isDispatchable();
    }

    /** Updates the position without allowing an older timestamp. */
    public void moveTo(GeoPoint point, LocalDateTime at) {
        ValidationUtil.requireNonNull(point, "ubicación");
        ValidationUtil.requireNonNull(at, "fecha de ubicación");

        if (lastLocationAt != null && at.isBefore(lastLocationAt)) {
            throw new ValidationException(
                    "La nueva fecha de ubicación no puede ser anterior a la registrada.");
        }

        this.lastLocation = point;
        this.lastLocationAt = at;
    }

    /** Changes the operational status of the vehicle. */
    public void changeStatus(VehicleOperationalStatus next) {
        this.operationalStatus = ValidationUtil.requireNonNull(
                next, "estado operativo");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof EmergencyVehicle that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "EmergencyVehicle{id=" + id
                + ", unitCode=" + unitCode
                + ", operationalStatus=" + operationalStatus + "}";
    }
}
