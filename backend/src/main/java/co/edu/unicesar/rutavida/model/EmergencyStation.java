
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.regex.Pattern;

/** An emergency response station with a geographic location. */
public class EmergencyStation implements Locatable {

    private static final Pattern PHONE =
            Pattern.compile("[0-9+ -]{7,20}");

    private long id;
    private final String name;
    private final String address;
    private final String neighborhood;
    private final String phone;
    private final GeoPoint location;
    private final RecordStatus status;

    public EmergencyStation(long id, String name, String address,
                            String neighborhood, String phone,
                            GeoPoint location, RecordStatus status) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.name = ValidationUtil.requireText(
                name, "nombre", 100);
        this.address = ValidationUtil.requireText(
                address, "dirección", 180);
        this.neighborhood = ValidationUtil.optionalText(
                neighborhood, "barrio", 100);

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
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
    }

    /** Assigns an identifier only to an unsaved station. */
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

    public String getAddress() {
        return address;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public GeoPoint getLocation() {
        return location;
    }

    public RecordStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof EmergencyStation that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "EmergencyStation{id=" + id
                + ", name=" + name
                + ", status=" + status + "}";
    }
}
