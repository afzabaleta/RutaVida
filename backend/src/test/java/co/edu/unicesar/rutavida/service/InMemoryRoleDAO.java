
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoleDAO;
import co.edu.unicesar.rutavida.model.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake RoleDAO for service tests.
 */
public class InMemoryRoleDAO implements RoleDAO {

    private final Map<Long, Role> roles = new LinkedHashMap<>();


    public void add(Role role) {
        roles.put(role.getId(), role);
    }


    @Override
    public Optional<Role> findById(long id) {
        return Optional.ofNullable(roles.get(id));
    }

    @Override
    public List<Role> findAll() {
        return new ArrayList<>(roles.values());
    }
}
