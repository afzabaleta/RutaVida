package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.EmergencyDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.Severity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * EmergencyDAO on Oracle (table EMERGENCIA). The status change is audited by the trigger
 * trg_emergencia_estado; the registration is audited by EmergencyService.
 */
public class JdbcEmergencyDAO extends AbstractJdbcDAO implements EmergencyDAO {

    private static final String SELECT =
            "SELECT id_emergencia, id_tipo_emergencia, id_usuario_reporta, direccion_referencia, latitud,"
                    + " longitud, fecha_hora_reporte, severidad, descripcion, estado, fecha_hora_cierre"
                    + " FROM emergencia";

    /** The report time is written explicitly (rule R10): the service sets it with its Clock. */
    private static final String INSERT =
            "INSERT INTO emergencia (id_tipo_emergencia, id_usuario_reporta, direccion_referencia, latitud,"
                    + " longitud, fecha_hora_reporte, severidad, descripcion, estado, fecha_hora_cierre)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS =
            "UPDATE emergencia SET estado = ?, fecha_hora_cierre = ? WHERE id_emergencia = ?";

    private static final RowMapper<Emergency> MAPPER = row -> new Emergency(
            row.getLong("id_emergencia"),
            row.getLong("id_tipo_emergencia"),
            row.getLong("id_usuario_reporta"),
            row.getString("direccion_referencia"),
            new GeoPoint(row.getDouble("latitud"), row.getDouble("longitud")),
            getLocalDateTime(row, "fecha_hora_reporte"),
            Severity.fromCode(row.getString("severidad")),
            row.getString("descripcion"),
            EmergencyStatus.fromCode(row.getString("estado")),
            getLocalDateTime(row, "fecha_hora_cierre"));

    public JdbcEmergencyDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public long insert(Emergency emergency) {
        Objects.requireNonNull(emergency, "emergency");
        if (emergency.getId() != 0) {
            throw new IllegalArgumentException("The emergency is already saved with id " + emergency.getId());
        }
        GeoPoint location = Objects.requireNonNull(emergency.getLocation(), "location");
        long id = insertReturningId(INSERT, "id_emergencia",
                emergency.getEmergencyTypeId(), emergency.getReportedByUserId(), emergency.getAddressReference(),
                location.latitude(), location.longitude(), emergency.getReportedAt(), emergency.getSeverity(),
                emergency.getDescription(), emergency.getStatus(), emergency.getClosedAt());
        emergency.setId(id);
        return id;
    }

    @Override
    public Optional<Emergency> findById(long id) {
        return queryOne(SELECT + " WHERE id_emergencia = ?", MAPPER, id);
    }

    @Override
    public List<Emergency> findActive() {
        return queryList(SELECT + " WHERE estado <> ? ORDER BY fecha_hora_reporte DESC, id_emergencia DESC",
                MAPPER, EmergencyStatus.CLOSED);
    }

    @Override
    public List<Emergency> findByPeriod(LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return queryList(SELECT + " WHERE fecha_hora_reporte BETWEEN ? AND ?"
                + " ORDER BY fecha_hora_reporte, id_emergencia", MAPPER, from, to);
    }

    @Override
    public void updateStatus(long id, EmergencyStatus status, LocalDateTime closedAt) {
        Objects.requireNonNull(status, "status");
        int rows = execute(UPDATE_STATUS, status, closedAt, id);
        if (rows == 0) {
            throw new NotFoundException("Emergencia", id);
        }
    }
}