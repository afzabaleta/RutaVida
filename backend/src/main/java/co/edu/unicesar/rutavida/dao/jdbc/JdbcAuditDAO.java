package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.AuditDAO;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.enums.AuditResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** AuditDAO on Oracle (table AUDITORIA). Insert and read only. */
public class JdbcAuditDAO extends AbstractJdbcDAO implements AuditDAO {

    private static final String SELECT =
            "SELECT id_auditoria, id_usuario, modulo, operacion, registro_id, fecha_hora,"
                    + " resultado, detalle FROM auditoria";

    private static final String ORDER = " ORDER BY fecha_hora, id_auditoria";

    private static final String INSERT =
            "INSERT INTO auditoria (id_usuario, modulo, operacion, registro_id, fecha_hora,"
                    + " resultado, detalle) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final RowMapper<AuditLog> MAPPER = row -> new AuditLog(
            row.getLong("id_auditoria"),
            getNullableLong(row, "id_usuario"),
            row.getString("modulo"),
            row.getString("operacion"),
            getNullableLong(row, "registro_id"),
            getLocalDateTime(row, "fecha_hora"),
            AuditResult.fromCode(row.getString("resultado")),
            row.getString("detalle"));

    public JdbcAuditDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public void insert(AuditLog log) {
        Objects.requireNonNull(log, "log");
        if (log.getId() != 0) {
            throw new IllegalArgumentException("The audit entry is already saved with id " + log.getId());
        }
        long id = insertReturningId(INSERT, "id_auditoria",
                log.getUserId(), log.getModule(), log.getOperation(), log.getRecordId(),
                log.getOccurredAt(), log.getResult(), log.getDetail());
        log.setId(id);
    }

    @Override
    public List<AuditLog> findByRecord(String module, long recordId) {
        Objects.requireNonNull(module, "module");
        return queryList(SELECT + " WHERE modulo = ? AND registro_id = ?" + ORDER, MAPPER,
                module.trim().toUpperCase(Locale.ROOT), recordId);
    }

    @Override
    public List<AuditLog> findByPeriod(LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return queryList(SELECT + " WHERE fecha_hora BETWEEN ? AND ?" + ORDER, MAPPER, from, to);
    }

    @Override
    public List<AuditLog> search(LocalDateTime from, LocalDateTime to, String module, Long userId,
                                 AuditResult result, int limit) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // Only the filters that were given; every value goes as a parameter, never in the text.
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE fecha_hora BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>(List.of(from, to));
        if (module != null) {
            sql.append(" AND modulo = ?");
            params.add(module.trim().toUpperCase(Locale.ROOT));
        }
        if (userId != null) {
            sql.append(" AND id_usuario = ?");
            params.add(userId);
        }
        if (result != null) {
            sql.append(" AND resultado = ?");
            params.add(result);
        }
        sql.append(" ORDER BY fecha_hora DESC, id_auditoria DESC FETCH FIRST ? ROWS ONLY");
        params.add(limit);
        return queryList(sql.toString(), MAPPER, params.toArray());
    }
}