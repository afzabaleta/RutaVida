package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * Reading of roles (table ROL) with their permissions (tables ROL_PERMISO and PERMISO).
 * Roles and permissions come from 02_datos_iniciales.sql; the application does not
 * create them.
 */
public interface RoleDAO {

    /** The role with all its permissions already loaded. */
    Optional<Role> findById(long id);

    /** All roles with their permissions, ordered by name. */
    List<Role> findAll();
}