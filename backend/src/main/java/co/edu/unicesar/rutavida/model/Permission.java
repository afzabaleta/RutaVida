
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.util.Locale;

/** Permission to perform an action within a system module. */
public class Permission {

    private long id;
    private final String module;
    private final String action;
    private final String description;

    public Permission(long id, String module, String action,
                      String description) {
        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.module = ValidationUtil.requireText(
                module, "módulo", 60).toUpperCase(Locale.ROOT);
        this.action = ValidationUtil.requireText(
                action, "acción", 40).toUpperCase(Locale.ROOT);
        this.description = ValidationUtil.optionalText(
                description, "descripción", 200);
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

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    /** Matches a module and action without case sensitivity. */
    public boolean matches(String module, String action) {
        return module != null
                && action != null
                && this.module.equalsIgnoreCase(module)
                && this.action.equalsIgnoreCase(action);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof Permission that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Permission{id=" + id
                + ", module=" + module
                + ", action=" + action + "}";
    }
}
