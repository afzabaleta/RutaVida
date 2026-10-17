package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit trail (table AUDITORIA). Append-only: there is no update or delete.
 * The status changes of EMERGENCIA, ASIGNACION_EMERGENCIA and INCIDENTE_VIAL are written
 * by the triggers of 04_plsql.sql; this DAO writes the rest (logins, business operations).
 */
public interface AuditDAO {

    /** Saves a new entry (id 0) and assigns the generated id to it with {@code assignId}. */
    void insert(AuditLog log);

    /** History of one record, oldest first (e.g. module EMERGENCIAS, id of the emergency). */
    List<AuditLog> findByRecord(String module, long recordId);

    /** Entries between {@code from} and {@code to}, both included, oldest first. */
    List<AuditLog> findByPeriod(LocalDateTime from, LocalDateTime to);
}