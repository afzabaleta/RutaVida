package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import java.util.Set;

/**
 * A system role with its permissions (table ROL).
 * Only data: the business rules live in the service layer.
 */
public class Role {

    private long id;
    private final String name;
    private final String description;
    private final RecordStatus status;
    private final Set<Permission> permissions;

    public Role(long id,
            String name,
            String description,
            RecordStatus status,
            Set<Permission> permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.permissions = permissions;
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

    public String getDescription() {
        return description;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
