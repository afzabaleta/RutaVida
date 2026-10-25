package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.EmergencyAssignment;

import java.util.List;
import java.util.Optional;

/**
 * Units sent to emergencies (table ASIGNACION_EMERGENCIA). Every status change is
 * audited by the trigger trg_asignacion_estado of 04_plsql.sql, not by this DAO.
 */
public interface EmergencyAssignmentDAO {

    /**
     * Dispatches the unit with the procedure sp_asignar_unidad (DT-05): it creates the
     * assignment, marks the unit OCUPADO and, if the emergency was REPORTADA, moves it to
     * ASIGNADA, all in the current transaction. The assignment must be new (id 0) and
     * ASIGNADA; the generated id is assigned to it. A unit that is not DISPONIBLE or a
     * closed emergency gives BusinessRuleException with the message of the procedure.
     */
    long insert(EmergencyAssignment assignment);

    Optional<EmergencyAssignment> findById(long id);

    /** Open assignments of one emergency, oldest first. An emergency may need several units. */
    List<EmergencyAssignment> findOpenByEmergency(long emergencyId);

    /** The open assignment of a unit, if it has one (Oracle allows at most one). */
    Optional<EmergencyAssignment> findOpenByVehicle(long vehicleId);

    /** Every assignment of one emergency, open or not, oldest first (its history). */
    List<EmergencyAssignment> findByEmergency(long emergencyId);

    /**
     * Saves what changes during the mission: status, departure and arrival.
     * Throws NotFoundException if the assignment does not exist.
     */
    void update(EmergencyAssignment assignment);
}