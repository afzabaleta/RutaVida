package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** EmergencyAssignmentDAO on Oracle (table ASIGNACION_EMERGENCIA and sp_asignar_unidad). */
public class JdbcEmergencyAssignmentDAO extends AbstractJdbcDAO implements EmergencyAssignmentDAO {

    private static final String ASSIGN = "{call sp_asignar_unidad(?, ?, ?, ?, ?)}";

    private static final String SELECT =
            "SELECT id_asignacion, id_emergencia, id_vehiculo, id_usuario_coordinador,"
                    + " fecha_asignacion, hora_salida, hora_llegada, estado FROM asignacion_emergencia";

    /** Same rule as AssignmentStatus.isOpen() and the unique index ux_asignacion_vehiculo_abierta. */
    private static final String OPEN = " estado NOT IN ('FINALIZADA', 'CANCELADA')";

    private static final String OLDEST_FIRST = " ORDER BY fecha_asignacion, id_asignacion";

    private static final String UPDATE =
            "UPDATE asignacion_emergencia SET estado = ?, hora_salida = ?, hora_llegada = ?"
                    + " WHERE id_asignacion = ?";

    private static final RowMapper<EmergencyAssignment> MAPPER = row -> new EmergencyAssignment(
            row.getLong("id_asignacion"),
            row.getLong("id_emergencia"),
            row.getLong("id_vehiculo"),
            row.getLong("id_usuario_coordinador"),
            getLocalDateTime(row, "fecha_asignacion"),
            getLocalDateTime(row, "hora_salida"),
            getLocalDateTime(row, "hora_llegada"),
            AssignmentStatus.fromCode(row.getString("estado")));

    public JdbcEmergencyAssignmentDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public long insert(EmergencyAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        if (assignment.getId() != 0) {
            throw new IllegalArgumentException("The assignment is already saved with id " + assignment.getId());
        }
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new IllegalArgumentException("A new assignment must be ASIGNADA, not " + assignment.getStatus());
        }
        long id = callProcedureReturningLong(ASSIGN,
                assignment.getEmergencyId(), assignment.getVehicleId(),
                assignment.getCoordinatorUserId(), assignment.getAssignedAt());
        assignment.setId(id);
        return id;
    }

    @Override
    public Optional<EmergencyAssignment> findById(long id) {
        return queryOne(SELECT + " WHERE id_asignacion = ?", MAPPER, id);
    }

    @Override
    public List<EmergencyAssignment> findOpenByEmergency(long emergencyId) {
        return queryList(SELECT + " WHERE id_emergencia = ? AND" + OPEN + OLDEST_FIRST, MAPPER, emergencyId);
    }

    @Override
    public Optional<EmergencyAssignment> findOpenByVehicle(long vehicleId) {
        return queryOne(SELECT + " WHERE id_vehiculo = ? AND" + OPEN, MAPPER, vehicleId);
    }

    @Override
    public List<EmergencyAssignment> findByEmergency(long emergencyId) {
        return queryList(SELECT + " WHERE id_emergencia = ?" + OLDEST_FIRST, MAPPER, emergencyId);
    }

    @Override
    public void update(EmergencyAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        if (assignment.getId() <= 0) {
            throw new IllegalArgumentException("Only a saved assignment can be updated");
        }
        int rows = execute(UPDATE, assignment.getStatus(), assignment.getDepartedAt(),
                assignment.getArrivedAt(), assignment.getId());
        if (rows == 0) {
            throw new NotFoundException("Asignación", assignment.getId());
        }
    }
}