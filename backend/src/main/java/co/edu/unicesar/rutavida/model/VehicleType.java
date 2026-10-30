package co.edu.unicesar.rutavida.model;

/**
 * Classification of an emergency vehicle (table TIPO_VEHICULO).
 * Only data: the business rules live in the service layer.
 */
public class VehicleType {

    private long id;
    private final String name;
    private final int priority;
    private final String description;

    public VehicleType(long id,
            String name,
            int priority,
            String description) {
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
