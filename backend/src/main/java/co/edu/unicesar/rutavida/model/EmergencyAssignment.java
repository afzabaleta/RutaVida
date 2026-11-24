package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import java.time.LocalDateTime;

/**
 * One unit sent to one emergency (table ASIGNACION_EMERGENCIA).
 * Only data: the business rules live in the service layer.
 */
public class EmergencyAssignment {

    private long id;
    private final long emergencyId;
    private final long vehicleId;
    private final long coordinatorUserId;
    private final LocalDateTime assignedAt;
    private LocalDateTime departedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime finishedAt;
    private AssignmentStatus status;

    public EmergencyAssignment(long id,
            long emergencyId,
            long vehicleId,
            long coordinatorUserId,
            LocalDateTime assignedAt,
            LocalDateTime departedAt,
            LocalDateTime arrivedAt,
            AssignmentStatus status) {
        this(id, emergencyId, vehicleId, coordinatorUserId, assignedAt, departedAt, arrivedAt, null, status);
    }

    public EmergencyAssignment(long id,
            long emergencyId,
            long vehicleId,
            long coordinatorUserId,
            LocalDateTime assignedAt,
            LocalDateTime departedAt,
            LocalDateTime arrivedAt,
            LocalDateTime finishedAt,
            AssignmentStatus status) {
        this.id = id;
        this.emergencyId = emergencyId;
        this.vehicleId = vehicleId;
        this.coordinatorUserId = coordinatorUserId;
        this.assignedAt = assignedAt;
        this.departedAt = departedAt;
        this.arrivedAt = arrivedAt;
        this.finishedAt = finishedAt;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getEmergencyId() {
        return emergencyId;
    }

    public long getVehicleId() {
        return vehicleId;
    }

    public long getCoordinatorUserId() {
        return coordinatorUserId;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getDepartedAt() {
        return departedAt;
    }

    public void setDepartedAt(LocalDateTime departedAt) {
        this.departedAt = departedAt;
    }

    public LocalDateTime getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(LocalDateTime arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    /** Time at which the field team confirmed the attention had ended. */
    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }
}
