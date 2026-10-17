package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.UserDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** UserDAO on Oracle (table USUARIO). */
public class JdbcUserDAO extends AbstractJdbcDAO implements UserDAO {

    private static final String COLUMNS =
            "id_usuario, id_rol, documento, nombres, apellidos, email, username,"
                    + " password_hash, estado, fecha_registro";

    private static final String SELECT = "SELECT " + COLUMNS + " FROM usuario";

    private static final String INSERT =
            "INSERT INTO usuario (id_rol, documento, nombres, apellidos, email, username,"
                    + " password_hash, estado, fecha_registro) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** fecha_registro is not updated: it is the creation date. */
    private static final String UPDATE =
            "UPDATE usuario SET id_rol = ?, documento = ?, nombres = ?, apellidos = ?, email = ?,"
                    + " username = ?, password_hash = ?, estado = ? WHERE id_usuario = ?";

    private static final RowMapper<User> MAPPER = row -> new User(
            row.getLong("id_usuario"),
            row.getLong("id_rol"),
            row.getString("documento"),
            row.getString("nombres"),
            row.getString("apellidos"),
            row.getString("email"),
            row.getString("username"),
            row.getString("password_hash"),
            RecordStatus.fromCode(row.getString("estado")),
            getLocalDateTime(row, "fecha_registro"));

    public JdbcUserDAO(ConnectionProvider connections) {
        super(connections);
    }

    @Override
    public Optional<User> findById(long id) {
        return queryOne(SELECT + " WHERE id_usuario = ?", MAPPER, id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return queryOne(SELECT + " WHERE username = ?", MAPPER,
                username.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public List<User> findAll() {
        return queryList(SELECT + " ORDER BY username", MAPPER);
    }

    @Override
    public long insert(User user) {
        Objects.requireNonNull(user, "user");
        if (user.getId() != 0) {
            throw new IllegalArgumentException("The user is already saved with id " + user.getId());
        }
        long id = insertReturningId(INSERT, "id_usuario",
                user.getRoleId(), user.getDocument(), user.getFirstNames(), user.getLastNames(),
                user.getEmail(), user.getUsername(), user.getPasswordHash(), user.getStatus(),
                user.getRegisteredAt());
        user.assignId(id);
        return id;
    }

    @Override
    public void update(User user) {
        Objects.requireNonNull(user, "user");
        if (user.getId() <= 0) {
            throw new IllegalArgumentException("Only a saved user can be updated");
        }
        int rows = execute(UPDATE,
                user.getRoleId(), user.getDocument(), user.getFirstNames(), user.getLastNames(),
                user.getEmail(), user.getUsername(), user.getPasswordHash(), user.getStatus(),
                user.getId());
        if (rows == 0) {
            throw new NotFoundException("Usuario", user.getId());
        }
    }
}