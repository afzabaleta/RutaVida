
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.regex.Pattern;

/** A hospital that can receive emergency patients. */
public class Hospital implements Locatable {

    private static final Pattern PHONE =
            Pattern.compile("[0-9+ -]{7,20}");

    private long id;
    private final String name;
    private final HospitalLevel level;
    private final String address;
    private final String phone;
    private final GeoPoint location;
    private final Integer emergencyCapacity;
    private final RecordStatus status;

    public Hospital(long id, String name, HospitalLevel level,
                    String address, String phone, GeoPoint location,
                    Integer emergencyCapacity, RecordStatus status) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.name = ValidationUtil.requireText(
                name, "nombre", 150);
        this.level = ValidationUtil.requireNonNull(
                level, "nivel hospitalario");
        this.address = ValidationUtil.requireText(
                address, "dirección", 180);

        String validatedPhone = ValidationUtil.optionalText(
                phone, "teléfono", 20);

        if (validatedPhone != null
                && !PHONE.matcher(validatedPhone).matches()) {
            throw new ValidationException(
                    "El teléfono debe tener entre 7 y 20 caracteres válidos.");
        }

        this.phone = validatedPhone;
        this.location = ValidationUtil.requireNonNull(
                location, "ubicación");

        if (emergencyCapacity != null && emergencyCapacity < 0) {
            throw new ValidationException(
                    "La capacidad de emergencias no puede ser negativa.");
        }

        this.emergencyCapacity = emergencyCapacity;
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
    }

    /** Assigns an identifier only to an unsaved hospital. */
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

    public String getName() {
        return name;
    }

    public HospitalLevel getLevel() {
        return level;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public GeoPoint getLocation() {
        return location;
    }

    public Integer getEmergencyCapacity() {
        return emergencyCapacity;
    }

    public RecordStatus getStatus() {
        return status;
    }

    /** Checks whether the hospital is operational. */
    public boolean isOperational() {
        return status == RecordStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof Hospital that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Hospital{id=" + id
                + ", name=" + name
                + ", level=" + level
                + ", status=" + status + "}";
    }
}
