package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * RoleDAO on Oracle (tables ROL, ROL_PERMISO and PERMISO). Role is immutable and receives
 * its permissions in the constructor, so the role row and its permissions are read first
 * and the Role is built after. findAll uses two queries in total, not one per role.
 */
public class JdbcRoleDAO extends AbstractJdbcDAO implements RoleDAO {

    private static final String SELECT_ROLE =
            "SELECT id_rol, nombre_rol, descripcion, estado FROM rol";

    private static final String SELECT_PERMISSIONS =
            "SELECT rp.id_rol, p.id_permiso, p.modulo, p.accion, p.descripcion"
                    + " FROM rol_permiso rp JOIN permiso p ON p.id_permiso = rp.id_permiso";

    /** Columns of ROL before the permissions are attached. */
    private record RoleRow(long id, String name, String description, RecordStatus status) {
    }

    /** One row of ROL_PERMISO joined with PERMISO. */
    private record RolePermission(long roleId, Permission permission) {
    }

    private static final RowMapper<RoleRow> ROLE_MAPPER = row -> new RoleRow(
            row.getLong("id_rol"),
            row.getString("nombre_rol"),
            row.getString("descripcion"),
            RecordStatus.fromCode(row.getString("estado")));

    private static final RowMapper<RolePermission> PERMISSION_MAPPER = row -> new RolePermission(
            row.getLong("id_rol"),
            new Permission(
                    row.getLong("id_permiso"),
                    row.getString("modulo"),
                    row.getString("accion"),
                    row.getString("descripcion")));

    public JdbcRoleDAO(ConnectionProvider connections) {
        super(connections);
    }

    @Override
    public Optional<Role> findById(long id) {
        return queryOne(SELECT_ROLE + " WHERE id_rol = ?", ROLE_MAPPER, id)
                .map(role -> build(role, permissionsByRole(" WHERE rp.id_rol = ?", id)));
    }

    @Override
    public List<Role> findAll() {
        List<RoleRow> roles = queryList(SELECT_ROLE + " ORDER BY nombre_rol", ROLE_MAPPER);
        Map<Long, Set<Permission>> permissions = permissionsByRole("");
        return roles.stream()
                .map(role -> build(role, permissions))
                .toList();
    }

    private Map<Long, Set<Permission>> permissionsByRole(String where, Object... params) {
        Map<Long, Set<Permission>> result = new HashMap<>();
        for (RolePermission row : queryList(SELECT_PERMISSIONS + where, PERMISSION_MAPPER, params)) {
            result.computeIfAbsent(row.roleId(), key -> new HashSet<>()).add(row.permission());
        }
        return result;
    }

    private static Role build(RoleRow role, Map<Long, Set<Permission>> permissions) {
        return new Role(role.id(), role.name(), role.description(), role.status(),
                permissions.getOrDefault(role.id(), Set.of()));
    }
}