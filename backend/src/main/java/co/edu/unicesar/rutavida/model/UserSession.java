
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.AccessDeniedException;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;

/**
 * Authenticated user session with the role and permissions
 * available at login time.
 */
public record UserSession(
        User user,
        Role role,
        LocalDateTime startedAt) {

    public UserSession {
        ValidationUtil.requireNonNull(user, "usuario");
        ValidationUtil.requireNonNull(role, "rol");
        ValidationUtil.requireNonNull(startedAt, "inicio de sesión");
    }

    /** Checks whether the user and role are active and permission exists. */
    public boolean hasPermission(String module, String action) {
        return user.isActive()
                && role.hasPermission(module, action);
    }

    /** Requires a permission or rejects the operation. */
    public void require(String module, String action) {
        if (!hasPermission(module, action)) {
            throw new AccessDeniedException(
                    "No tiene permiso para realizar esta operación.");
        }
    }
}
