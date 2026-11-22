
package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.AlertDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.enums.AlertType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AlertDAO on Oracle (table ALERTA).
 * The index ix_alerta_usuario_leida serves the queries.
 */
public class JdbcAlertDAO extends AbstractJdbcDAO implements AlertDAO {

    private static final String SELECT =
            "SELECT id_alerta, id_emergencia, id_usuario, tipo_alerta, mensaje, fecha_hora, leida FROM alerta";

    private static final String INSERT =
            "INSERT INTO alerta (id_emergencia, id_usuario, tipo_alerta, mensaje, fecha_hora, leida)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";

    private static final String NEWEST_FIRST =
            " ORDER BY fecha_hora DESC, id_alerta DESC";

    private static final RowMapper<Alert> MAPPER = row -> new Alert(
            row.getLong("id_alerta"),
            getNullableLong(row, "id_emergencia"),
            row.getLong("id_usuario"),
            AlertType.fromCode(row.getString("tipo_alerta")),
            row.getString("mensaje"),
            getLocalDateTime(row, "fecha_hora"),
            getFlag(row, "leida"));

    public JdbcAlertDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public long insert(Alert alert) {
        Objects.requireNonNull(alert, "alert");

        if (alert.getId() != 0) {
            throw new IllegalArgumentException(
                    "The alert is already saved with id " + alert.getId());
        }

        long id = insertReturningId(
                INSERT,
                "id_alerta",
                alert.getEmergencyId(),
                alert.getUserId(),
                alert.getType(),
                alert.getMessage(),
                alert.getCreatedAt(),
                alert.isRead());

        alert.setId(id);
        return id;
    }

    @Override
    public Optional<Alert> findById(long id) {
        return queryOne(SELECT + " WHERE id_alerta = ?", MAPPER, id);
    }

    @Override
    public List<Alert> findByUser(long userId, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        return queryList(
                SELECT + " WHERE id_usuario = ?" + NEWEST_FIRST
                        + " FETCH FIRST ? ROWS ONLY",
                MAPPER,
                userId,
                limit);
    }

    @Override
    public List<Alert> findUnreadByUser(long userId) {
        return queryList(
                SELECT + " WHERE id_usuario = ? AND leida = ?" + NEWEST_FIRST,
                MAPPER,
                userId,
                false);
    }

    @Override
    public void markAsRead(long id) {
        if (execute(
                "UPDATE alerta SET leida = ? WHERE id_alerta = ?",
                true,
                id) == 0) {
            throw new NotFoundException("Alerta", id);
        }
    }
}
