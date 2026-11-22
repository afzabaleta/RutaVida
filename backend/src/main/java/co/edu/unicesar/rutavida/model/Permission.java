package co.edu.unicesar.rutavida.model;

/**
 * Permission to perform an action within a module (table PERMISO).
 * Only data: the business rules live in the service layer.
 */
public class Permission {

    private long id;
    private final String module;
    private final String action;
    private final String description;

    public Permission(long id,
            String module,
            String action,
            String description) {
        this.id = id;
        this.module = module;
        this.action = action;
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }
}
