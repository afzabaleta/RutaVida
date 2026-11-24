package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.PanicReportDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PanicReportDAO on Oracle (table REPORTE_PANICO of 07_boton_panico.sql).
 * The indexes ix_reporte_panico_estado_fecha, ix_reporte_panico_dispositivo and
 * ix_reporte_panico_usuario (08_ciudadanos.sql) serve the queries of the last minutes.
 */
public class JdbcPanicReportDAO extends AbstractJdbcDAO implements PanicReportDAO {

    private static final String SELECT =
            "SELECT id_reporte, latitud, longitud, fecha_hora, id_dispositivo, id_usuario, id_tipo_emergencia,"
                    + " descripcion, estado, id_emergencia FROM reporte_panico";

    private static final String INSERT =
            "INSERT INTO reporte_panico (latitud, longitud, fecha_hora, id_dispositivo, id_usuario,"
                    + " id_tipo_emergencia, descripcion, estado, id_emergencia) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final RowMapper<PanicReport> MAPPER = row -> new PanicReport(
            row.getLong("id_reporte"),
            new GeoPoint(row.getDouble("latitud"), row.getDouble("longitud")),
            getLocalDateTime(row, "fecha_hora"),
            row.getString("id_dispositivo"),
            getNullableLong(row, "id_usuario"),
            getNullableLong(row, "id_tipo_emergencia"),
            row.getString("descripcion"),
            PanicReportStatus.fromCode(row.getString("estado")),
            getNullableLong(row, "id_emergencia"));

    public JdbcPanicReportDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public void lockValidation(long systemUserId) {
        queryOne("SELECT id_usuario FROM usuario WHERE id_usuario = ? FOR UPDATE",
                row -> row.getLong("id_usuario"), systemUserId)
                .orElseThrow(() -> new NotFoundException("Usuario del sistema", systemUserId));
    }

    @Override
    public long insert(PanicReport report) {
        Objects.requireNonNull(report, "report");
        if (report.getId() != 0) {
            throw new IllegalArgumentException("The report is already saved with id " + report.getId());
        }
        GeoPoint location = Objects.requireNonNull(report.getLocation(), "location");
        long id = insertReturningId(INSERT, "id_reporte",
                location.latitude(), location.longitude(), report.getReportedAt(), report.getDeviceId(),
                report.getUserId(), report.getEmergencyTypeId(), report.getDescription(), report.getStatus(),
                report.getEmergencyId());
        report.setId(id);
        return id;
    }

    @Override
    public Optional<PanicReport> findById(long id) {
        return queryOne(SELECT + " WHERE id_reporte = ?", MAPPER, id);
    }

    @Override
    public List<PanicReport> findPendingSince(LocalDateTime since) {
        return queryList(SELECT + " WHERE estado = ? AND fecha_hora >= ? ORDER BY fecha_hora, id_reporte",
                MAPPER, PanicReportStatus.PENDING, Objects.requireNonNull(since, "since"));
    }

    @Override
    public List<PanicReport> findSince(LocalDateTime since) {
        return queryList(SELECT + " WHERE fecha_hora >= ? ORDER BY fecha_hora DESC, id_reporte DESC",
                MAPPER, Objects.requireNonNull(since, "since"));
    }

    @Override
    public int countByDeviceSince(String deviceId, LocalDateTime since) {
        return queryOne("SELECT COUNT(*) AS cantidad FROM reporte_panico WHERE id_dispositivo = ? AND fecha_hora >= ?",
                row -> row.getInt("cantidad"), Objects.requireNonNull(deviceId, "deviceId"),
                Objects.requireNonNull(since, "since")).orElse(0);
    }

    @Override
    public int countByUserSince(long userId, LocalDateTime since) {
        return queryOne("SELECT COUNT(*) AS cantidad FROM reporte_panico WHERE id_usuario = ? AND fecha_hora >= ?",
                row -> row.getInt("cantidad"), userId, Objects.requireNonNull(since, "since")).orElse(0);
    }

    @Override
    public int countByUserAndStatusSince(long userId, PanicReportStatus status, LocalDateTime since) {
        return queryOne("SELECT COUNT(*) AS cantidad FROM reporte_panico"
                        + " WHERE id_usuario = ? AND estado = ? AND fecha_hora >= ?",
                row -> row.getInt("cantidad"), userId, Objects.requireNonNull(status, "status"),
                Objects.requireNonNull(since, "since")).orElse(0);
    }

    @Override
    public void markValidated(Collection<Long> ids, long emergencyId) {
        Objects.requireNonNull(ids, "ids");
        for (Long id : ids) {
            if (execute("UPDATE reporte_panico SET estado = ?, id_emergencia = ? WHERE id_reporte = ?",
                    PanicReportStatus.VALIDATED, emergencyId, id) == 0) {
                throw new NotFoundException("Reporte de pánico", id);
            }
        }
    }

    @Override
    public void updateStatus(long id, PanicReportStatus status) {
        Objects.requireNonNull(status, "status");
        if (status == PanicReportStatus.VALIDATED) {
            throw new IllegalArgumentException("A validated report needs its emergency: use markValidated");
        }
        if (execute("UPDATE reporte_panico SET estado = ?, id_emergencia = NULL WHERE id_reporte = ?",
                status, id) == 0) {
            throw new NotFoundException("Reporte de pánico", id);
        }
    }

    @Override
    public int expirePendingBefore(LocalDateTime before) {
        return execute("UPDATE reporte_panico SET estado = ? WHERE estado = ? AND fecha_hora < ?",
                PanicReportStatus.EXPIRED, PanicReportStatus.PENDING, Objects.requireNonNull(before, "before"));
    }
}
