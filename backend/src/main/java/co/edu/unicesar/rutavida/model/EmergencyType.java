package co.edu.unicesar.rutavida.model;

/**
 * Classification of an emergency (table TIPO_EMERGENCIA).
 * Only data: the business rules live in the service layer.
 */
public class EmergencyType {

    private long id;
    private final String name;
    private final int priority;
    private final String description;

    public EmergencyType(long id,
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
