
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.Locale;
import java.util.Set;

/** A system role with its assigned permissions (table ROL). */
public class Role {

    private long id;
    private final String name;
    private final String description;
    private final RecordStatus status;
    private final Set<Permission> permissions;

    public Role(long id, String name, String description,
                RecordStatus status, Set<Permission> permissions) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.name = ValidationUtil.requireText(
                name, "nombre del rol", 50).toUpperCase(Locale.ROOT);
        this.description = ValidationUtil.optionalText(
                description, "descripción", 200);
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
        this.permissions = Set.copyOf(
                ValidationUtil.requireNonNull(
                        permissions, "permisos"));
    }

    /** Assigns an identifier only to an unsaved entity. */
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

    public String getDescription() {
        return description;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /** An inactive role cannot grant permissions. */
    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    /** Checks an exact module and action in an active role. */
    public boolean hasPermission(String module, String action) {
        if (!isActive()) {
            return false;
        }

        return permissions.stream()
                .anyMatch(permission ->
                        permission.matches(module, action));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof Role that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Role{id=" + id
                + ", name=" + name
                + ", status=" + status + "}";
    }
}
