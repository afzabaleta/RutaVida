package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.RoadIncidentDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.Severity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** RoadIncidentDAO on Oracle (table INCIDENTE_VIAL). */
public class JdbcRoadIncidentDAO extends AbstractJdbcDAO implements RoadIncidentDAO {

    private static final String SELECT =
            "SELECT id_incidente, id_tramo, tipo_incidente, severidad, bloquea_tramo, descripcion,"
                    + " fecha_inicio, fecha_fin, estado, origen FROM incidente_vial";

    private static final String INSERT =
            "INSERT INTO incidente_vial (id_tramo, tipo_incidente, severidad, bloquea_tramo,"
                    + " descripcion, fecha_inicio, fecha_fin, estado, origen)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Only what RoadIncident lets change: activate, reject and close. */
    private static final String UPDATE =
            "UPDATE incidente_vial SET bloquea_tramo = ?, fecha_fin = ?, estado = ?"
                    + " WHERE id_incidente = ?";

    private static final RowMapper<RoadIncident> MAPPER = row -> new RoadIncident(
            row.getLong("id_incidente"),
            row.getLong("id_tramo"),
            IncidentType.fromCode(row.getString("tipo_incidente")),
            Severity.fromCode(row.getString("severidad")),
            getFlag(row, "bloquea_tramo"),
            row.getString("descripcion"),
            getLocalDateTime(row, "fecha_inicio"),
            getLocalDateTime(row, "fecha_fin"),
            IncidentStatus.fromCode(row.getString("estado")),
            IncidentOrigin.fromCode(row.getString("origen")));

    public JdbcRoadIncidentDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public long insert(RoadIncident incident) {
        Objects.requireNonNull(incident, "incident");
        if (incident.getId() != 0) {
            throw new IllegalArgumentException("The incident is already saved with id " + incident.getId());
        }
        long id = insertReturningId(INSERT, "id_incidente",
                incident.getSegmentId(), incident.getType(), incident.getSeverity(),
                incident.isBlocksSegment(), incident.getDescription(), incident.getStartedAt(),
                incident.getEndedAt(), incident.getStatus(), incident.getOrigin());
        incident.setId(id);
        return id;
    }

    @Override
    public Optional<RoadIncident> findById(long id) {
        return queryOne(SELECT + " WHERE id_incidente = ?", MAPPER, id);
    }

    @Override
    public List<RoadIncident> findByStatus(IncidentStatus status) {
        Objects.requireNonNull(status, "status");
        return queryList(SELECT + " WHERE estado = ? ORDER BY fecha_inicio, id_incidente", MAPPER, status);
    }

    @Override
    public void update(RoadIncident incident) {
        Objects.requireNonNull(incident, "incident");
        if (incident.getId() <= 0) {
            throw new IllegalArgumentException("Only a saved incident can be updated");
        }
        int rows = execute(UPDATE,
                incident.isBlocksSegment(), incident.getEndedAt(), incident.getStatus(), incident.getId());
        if (rows == 0) {
            throw new NotFoundException("Incidente vial", incident.getId());
        }
    }
}