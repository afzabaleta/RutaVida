
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.Locale;

/** Classification of an emergency vehicle (table TIPO_VEHICULO). */
public class VehicleType {

    private long id;
    private final String name;
    private final int priority;
    private final String description;

    public VehicleType(long id, String name, int priority,
                       String description) {
        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.name = ValidationUtil.requireText(
                name, "nombre", 50).toUpperCase(Locale.ROOT);
        this.priority = (int) ValidationUtil.requireInRange(
                priority, 1, 10, "prioridad");
        this.description = ValidationUtil.optionalText(
                description, "descripción", 200);
    }

    /** Called by the DAO after the INSERT. Only works while the id is still 0. */
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

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof VehicleType that
                && id != 0 && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "VehicleType{id=" + id
                + ", name=" + name
                + ", priority=" + priority + "}";
    }
}
