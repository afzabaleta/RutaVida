package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Business rules of dispatching a unit (CU-09, CU-10): which units can be sent and
 * how a mission advances. Saving the assignment is added with the remaining DAOs.
 */
public class DispatchService {

    /** Mission: ASIGNADA → EN_RUTA → EN_SITIO → TRASLADANDO → FINALIZADA, or CANCELADA before arriving. */
    private static final Map<AssignmentStatus, Set<AssignmentStatus>> NEXT = Map.of(
            AssignmentStatus.ASSIGNED, EnumSet.of(AssignmentStatus.EN_ROUTE, AssignmentStatus.CANCELLED),
            AssignmentStatus.EN_ROUTE, EnumSet.of(AssignmentStatus.ON_SCENE, AssignmentStatus.CANCELLED),
            AssignmentStatus.ON_SCENE, EnumSet.of(AssignmentStatus.TRANSPORTING, AssignmentStatus.FINISHED),
            AssignmentStatus.TRANSPORTING, EnumSet.of(AssignmentStatus.FINISHED),
            AssignmentStatus.FINISHED, EnumSet.noneOf(AssignmentStatus.class),
            AssignmentStatus.CANCELLED, EnumSet.noneOf(AssignmentStatus.class));

    /** Only available units can be sent to a new emergency. */
    public boolean isDispatchable(EmergencyVehicle vehicle) {
        return vehicle.getOperationalStatus() == VehicleOperationalStatus.AVAILABLE;
    }

    /** Checks a new assignment before saving it. */
    public void validateNew(EmergencyAssignment assignment) {
        ValidationUtil.requireNonNull(assignment, "asignación");
        ValidationUtil.requirePositiveId(assignment.getEmergencyId(), "emergencia");
        ValidationUtil.requirePositiveId(assignment.getVehicleId(), "unidad");
        ValidationUtil.requirePositiveId(assignment.getCoordinatorUserId(), "coordinador");
        ValidationUtil.requireNonNull(assignment.getAssignedAt(), "fecha de asignación");
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new ValidationException("Una asignación nueva debe iniciar en estado ASIGNADA.");
        }
    }

    public boolean canChange(AssignmentStatus from, AssignmentStatus to) {
        return from != null && to != null && NEXT.get(from).contains(to);
    }

    /** Advances the mission; EN_RUTA records the departure and EN_SITIO the arrival. */
    public void changeStatus(EmergencyAssignment assignment, AssignmentStatus next, LocalDateTime at) {
        ValidationUtil.requireNonNull(next, "nuevo estado");
        ValidationUtil.requireNonNull(at, "fecha del cambio");
        if (!canChange(assignment.getStatus(), next)) {
            throw new BusinessRuleException("No se puede cambiar la asignación de "
                    + assignment.getStatus().code() + " a " + next.code() + ".");
        }
        if (at.isBefore(assignment.getAssignedAt())) {
            throw new ValidationException("La fecha del cambio no puede ser anterior a la asignación.");
        }
        if (next == AssignmentStatus.EN_ROUTE) {
            assignment.setDepartedAt(at);
        }
        if (next == AssignmentStatus.ON_SCENE) {
            assignment.setArrivedAt(at);
        }
        assignment.setStatus(next);
    }

    /** True while the unit is still busy with this mission. */
    public boolean isOpen(EmergencyAssignment assignment) {
        return assignment.getStatus() != AssignmentStatus.FINISHED
                && assignment.getStatus() != AssignmentStatus.CANCELLED;
    }

    /** Updates the last known position of a unit; it cannot go back in time. */
    public void moveVehicle(EmergencyVehicle vehicle, GeoPoint point, LocalDateTime at) {
        ValidationUtil.requireLocation(point);
        ValidationUtil.requireNonNull(at, "fecha de ubicación");
        if (vehicle.getLastLocationAt() != null && at.isBefore(vehicle.getLastLocationAt())) {
            throw new ValidationException(
                    "La nueva fecha de ubicación no puede ser anterior a la registrada.");
        }
        vehicle.setLastLocation(point);
        vehicle.setLastLocationAt(at);
    }
}
