package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.CitizenSessionDAO;
import co.edu.unicesar.rutavida.model.CitizenSession;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * CitizenSessionDAO on Oracle (table SESION_CIUDADANO of 08_ciudadanos.sql). The token hash
 * is unique (uq_sesion_ciudadano_token), so a lookup reads one row through its index.
 */
public class JdbcCitizenSessionDAO extends AbstractJdbcDAO implements CitizenSessionDAO {

    private static final String SELECT =
            "SELECT id_sesion, id_usuario, token_hash, fecha_creacion, fecha_vencimiento, revocada"
                    + " FROM sesion_ciudadano";

    private static final String INSERT =
            "INSERT INTO sesion_ciudadano (id_usuario, token_hash, fecha_creacion, fecha_vencimiento, revocada)"
                    + " VALUES (?, ?, ?, ?, ?)";

    private static final RowMapper<CitizenSession> MAPPER = row -> new CitizenSession(
            row.getLong("id_sesion"),
            row.getLong("id_usuario"),
            row.getString("token_hash"),
            getLocalDateTime(row, "fecha_creacion"),
            getLocalDateTime(row, "fecha_vencimiento"),
            getFlag(row, "revocada"));

    public JdbcCitizenSessionDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public long insert(CitizenSession session) {
        Objects.requireNonNull(session, "session");
        if (session.getId() != 0) {
            throw new IllegalArgumentException("The session is already saved with id " + session.getId());
        }
        long id = insertReturningId(INSERT, "id_sesion",
                session.getUserId(), session.getTokenHash(), session.getCreatedAt(), session.getExpiresAt(),
                session.isRevoked());
        session.setId(id);
        return id;
    }

    @Override
    public Optional<CitizenSession> findValid(String tokenHash, LocalDateTime now) {
        return queryOne(SELECT + " WHERE token_hash = ? AND revocada = 'N' AND fecha_vencimiento > ?",
                MAPPER, Objects.requireNonNull(tokenHash, "tokenHash"), Objects.requireNonNull(now, "now"));
    }

    @Override
    public int revoke(String tokenHash) {
        return execute("UPDATE sesion_ciudadano SET revocada = 'S' WHERE token_hash = ? AND revocada = 'N'",
                Objects.requireNonNull(tokenHash, "tokenHash"));
    }

    @Override
    public int revokeAllOf(long userId) {
        return execute("UPDATE sesion_ciudadano SET revocada = 'S' WHERE id_usuario = ? AND revocada = 'N'", userId);
    }
}
