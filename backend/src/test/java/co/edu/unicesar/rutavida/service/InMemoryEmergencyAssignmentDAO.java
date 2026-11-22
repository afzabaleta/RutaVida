package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyAssignmentDAO;
import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.EmergencyVehicle;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake EmergencyAssignmentDAO for tests, without Oracle. insert() does what
 * sp_asignar_unidad does in Oracle: the unit becomes OCUPADO and a REPORTADA emergency
 * becomes ASIGNADA. It keeps copies, like a database.
 */
public class InMemoryEmergencyAssignmentDAO implements EmergencyAssignmentDAO {

    private final Map<Long, EmergencyAssignment> saved = new LinkedHashMap<>();
    private final InMemoryEmergencyVehicleDAO vehicles;
    private final InMemoryEmergencyDAO emergencies;
    private long nextId = 1;

    public InMemoryEmergencyAssignmentDAO(InMemoryEmergencyVehicleDAO vehicles, InMemoryEmergencyDAO emergencies) {
        this.vehicles = vehicles;
        this.emergencies = emergencies;
    }

    @Override
    public long insert(EmergencyAssignment assignment) {
        Emergency emergency = emergencies.findById(assignment.getEmergencyId())
                .orElseThrow(() -> new BusinessRuleException("La emergencia no existe"));
        EmergencyVehicle vehicle = vehicles.findById(assignment.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("La unidad no existe"));
        if (vehicle.getOperationalStatus() != VehicleOperationalStatus.AVAILABLE) {
            throw new BusinessRuleException("La unidad no esta disponible");
        }

        long id = nextId++;
        assignment.setId(id);
        saved.put(id, copy(assignment));
        vehicles.updateStatus(vehicle.getId(), VehicleOperationalStatus.BUSY);
        if (emergency.getStatus() == EmergencyStatus.REPORTED) {
            emergencies.updateStatus(emergency.getId(), EmergencyStatus.ASSIGNED, null);
        }
        return id;
    }

    @Override
    public Optional<EmergencyAssignment> findById(long id) {
        return Optional.ofNullable(saved.get(id)).map(InMemoryEmergencyAssignmentDAO::copy);
    }

    @Override
    public List<EmergencyAssignment> findOpenByEmergency(long emergencyId) {
        return findByEmergency(emergencyId).stream().filter(InMemoryEmergencyAssignmentDAO::isOpen).toList();
    }

    @Override
    public Optional<EmergencyAssignment> findOpenByVehicle(long vehicleId) {
        return saved.values().stream()
                .filter(assignment -> assignment.getVehicleId() == vehicleId && isOpen(assignment))
                .findFirst()
                .map(InMemoryEmergencyAssignmentDAO::copy);
    }

    @Override
    public List<EmergencyAssignment> findByEmergency(long emergencyId) {
        return saved.values().stream()
                .filter(assignment -> assignment.getEmergencyId() == emergencyId)
                .map(InMemoryEmergencyAssignmentDAO::copy)
                .toList();
    }

    @Override
    public void update(EmergencyAssignment assignment) {
        if (!saved.containsKey(assignment.getId())) {
            throw new NotFoundException("Asignación", assignment.getId());
        }
        saved.put(assignment.getId(), copy(assignment));
    }

    private static boolean isOpen(EmergencyAssignment assignment) {
        return assignment.getStatus() != AssignmentStatus.FINISHED
                && assignment.getStatus() != AssignmentStatus.CANCELLED;
    }

    private static EmergencyAssignment copy(EmergencyAssignment source) {
        return new EmergencyAssignment(source.getId(), source.getEmergencyId(), source.getVehicleId(),
                source.getCoordinatorUserId(), source.getAssignedAt(), source.getDepartedAt(),
                source.getArrivedAt(), source.getStatus());
    }
}