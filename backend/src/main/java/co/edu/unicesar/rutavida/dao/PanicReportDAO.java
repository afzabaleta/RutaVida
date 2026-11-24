package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Reports of the panic button (table REPORTE_PANICO, CU-22). */
public interface PanicReportDAO {

    /** Serialize panic validation on the existing system account for the current transaction. */
    void lockValidation(long systemUserId);

    /** Saves a new report (id 0), assigns the generated id with {@code setId} and returns it. */
    long insert(PanicReport report);

    Optional<PanicReport> findById(long id);

    /** PENDIENTE reports from {@code since} on, oldest first: the candidates to validate an alert. */
    List<PanicReport> findPendingSince(LocalDateTime since);

    /** Every report from {@code since} on, whatever its status, newest first: the map of the panel. */
    List<PanicReport> findSince(LocalDateTime since);

    /** How many reports a device sent from {@code since} on: the limit per device. */
    int countByDeviceSince(String deviceId, LocalDateTime since);

    /** Reports of one person since a moment: at most 3 every 10 minutes (RN-13, DT-13). */
    int countByUserSince(long userId, LocalDateTime since);

    /** Reports of one person with a status since a moment: the false alarms that block (RN-16). */
    int countByUserAndStatusSince(long userId, PanicReportStatus status, LocalDateTime since);

    /**
     * Marks the reports VALIDADO and links them to the emergency they created.
     * Throws NotFoundException if one of them does not exist.
     */
    void markValidated(Collection<Long> ids, long emergencyId);

    /**
     * Changes the status of one report to DESCARTADO or VENCIDO (VALIDADO goes through
     * {@link #markValidated}). Throws NotFoundException if it does not exist.
     */
    void updateStatus(long id, PanicReportStatus status);

    /** Every PENDIENTE report older than {@code before} becomes VENCIDO; returns how many. */
    int expirePendingBefore(LocalDateTime before);
}
