
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake UserDAO for service tests:
 * keeps users in memory without Oracle.
 */

public class InMemoryUserDAO implements UserDAO {


    private final Map<Long, User> users = new LinkedHashMap<>();

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }

        String wanted = username;

        return users.values().stream()
                .filter(u -> u.getUsername().equals(wanted))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    @Override
    public long insert(User user) {
        long id = users.size() + 1L;

        user.setId(id);
        users.put(id, user);

        return id;
    }

    @Override
    public void update(User user) {
        users.put(user.getId(), user);
    }
}
