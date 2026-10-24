
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One unit sent to one emergency (ASIGNACION_EMERGENCIA).
 *
 * <p>Mission: ASIGNADA → EN_RUTA (sets departedAt) → EN_SITIO
 * (sets arrivedAt) → TRASLADANDO → FINALIZADA, or CANCELADA
 * before arriving. Allowed changes come from AssignmentStatus.
 *
 * <p>The assignment is created by sp_asignar_unidad, and
 * trg_asignacion_estado audits status changes in Oracle.
 */
public class EmergencyAssignment {

    private long id;
    private final long emergencyId;
    private final long vehicleId;
    private final long coordinatorUserId;
    private final LocalDateTime assignedAt;
    private LocalDateTime departedAt;
    private LocalDateTime arrivedAt;
    private AssignmentStatus status;

    public EmergencyAssignment(long id, long emergencyId, long vehicleId,
                               long coordinatorUserId, LocalDateTime assignedAt,
                               LocalDateTime departedAt, LocalDateTime arrivedAt,
                               AssignmentStatus status) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.emergencyId = ValidationUtil.requirePositiveId(
                emergencyId, "emergencia");
        this.vehicleId = ValidationUtil.requirePositiveId(
                vehicleId, "unidad");
        this.coordinatorUserId = ValidationUtil.requirePositiveId(
                coordinatorUserId, "coordinador");
        this.assignedAt = ValidationUtil.requireNonNull(
                assignedAt, "fecha de asignación");
        this.status = ValidationUtil.requireNonNull(
                status, "estado");

        validateTimes(status, departedAt, arrivedAt);
        this.departedAt = departedAt;
        this.arrivedAt = arrivedAt;
    }

    /**
     * Validates chronological order and the timestamps required
     * for each assignment status.
     */
    private void validateTimes(AssignmentStatus state,
                               LocalDateTime departure,
                               LocalDateTime arrival) {

        if (departure != null && departure.isBefore(assignedAt)) {
            throw new ValidationException(
                    "La hora de salida no puede ser anterior a la asignación.");
        }

        if (arrival != null && (departure == null ||
                arrival.isBefore(departure))) {
            throw new ValidationException(
                    "La hora de llegada debe ser posterior a la salida.");
        }

        boolean mustHaveLeft = state == AssignmentStatus.EN_ROUTE
                || hasArrived(state);

        if (mustHaveLeft && departure == null) {
            throw new ValidationException(
                    "Una unidad en estado " + state.code()
                            + " debe tener hora de salida.");
        }

        if (hasArrived(state) && arrival == null) {
            throw new ValidationException(
                    "Una unidad en estado " + state.code()
                            + " debe tener hora de llegada.");
        }

        if (state == AssignmentStatus.ASSIGNED && departure != null) {
            throw new ValidationException(
                    "Una unidad ASIGNADA todavía no tiene hora de salida.");
        }

        if (!hasArrived(state) && arrival != null) {
            throw new ValidationException(
                    "Una unidad en estado " + state.code()
                            + " todavía no ha llegado.");
        }
    }

    private static boolean hasArrived(AssignmentStatus state) {
        return state == AssignmentStatus.ON_SCENE
                || state == AssignmentStatus.TRANSPORTING
                || state == AssignmentStatus.FINISHED;
    }

    /** Assigns an identifier only to an unsaved assignment. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }
        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    /**
     * Changes the mission status and records departure or arrival.
     * Invalid transitions throw BusinessRuleException.
     */
    public void changeStatus(AssignmentStatus next, LocalDateTime at) {
        ValidationUtil.requireNonNull(next, "nuevo estado");
        ValidationUtil.requireNonNull(at, "fecha del cambio");

        if (!status.canChangeTo(next)) {
            throw new BusinessRuleException(
                    "No se puede cambiar la asignación de "
                            + status.code() + " a " + next.code() + ".");
        }

        LocalDateTime nextDeparture =
                next == AssignmentStatus.EN_ROUTE ? at : departedAt;
        LocalDateTime nextArrival =
                next == AssignmentStatus.ON_SCENE ? at : arrivedAt;

        validateTimes(next, nextDeparture, nextArrival);

        this.status = next;
        this.departedAt = nextDeparture;
        this.arrivedAt = nextArrival;
    }

    /** True while the unit is still busy with this mission. */
    public boolean isOpen() {
        return status.isOpen();
    }

    /**
     * Time from assignment until arrival, or null if not yet arrived.
     * This is different from the official emergency response time.
     */
    public Duration getTimeToArrival() {
        return arrivedAt == null
                ? null
                : Duration.between(assignedAt, arrivedAt);
    }

    public long getId() {
        return id;
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

    public LocalDateTime getArrivedAt() {
        return arrivedAt;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof EmergencyAssignment that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "EmergencyAssignment{id=" + id
                + ", emergencyId=" + emergencyId
                + ", vehicleId=" + vehicleId
                + ", status=" + status + "}";
    }
}
