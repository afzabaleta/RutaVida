package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence of emergencies (table EMERGENCIA). */
public interface EmergencyDAO {

    /**
     * Saves a new emergency (id 0), assigns the generated id to it with {@code setId} and
     * returns that id. It joins the caller's transaction if there is one.
     */
    long insert(Emergency emergency);

    Optional<Emergency> findById(long id);

    /** Emergencies that are not CERRADA, newest report first. */
    List<Emergency> findActive();

    /** Emergencies reported between {@code from} and {@code to} (both included), oldest first. */
    List<Emergency> findByPeriod(LocalDateTime from, LocalDateTime to);

    /**
     * Changes the status and the closing time (null unless the status is CERRADA).
     * The trigger trg_emergencia_estado audits the change. Throws NotFoundException if
     * the emergency does not exist.
     */
    void updateStatus(long id, EmergencyStatus status, LocalDateTime closedAt);

    /**
     * Closes the emergency with sp_cerrar_emergencia (CU-12), in the caller's transaction: its
     * open assignments end (FINALIZADA if the unit arrived, CANCELADA if not), their units
     * become DISPONIBLE, its ACTIVA routes FINALIZADA and its PROPUESTA routes DESCARTADA, and
     * the emergency CERRADA at {@code closedAt}. Only from REPORTADA, EN_SITIO or TRASLADANDO;
     * otherwise, or if it does not exist, a BusinessRuleException with the procedure's message.
     */
    void close(long id, LocalDateTime closedAt);
}
