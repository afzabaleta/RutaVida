package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.util.ValidationUtil;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * One entry of the audit trail (table AUDITORIA). Append-only history: it has no methods
 * that change it after creation.
 *
 * <p>{@code userId} is null for actions without an authenticated user (a failed login,
 * the simulator) and {@code recordId} is null when the action is not about one record.
 */
public class AuditLog {

    private long id;
    private final Long userId;
    private final String module;
    private final String operation;
    private final Long recordId;
    private final LocalDateTime occurredAt;
    private final AuditResult result;
    private final String detail;

    public AuditLog(long id, Long userId, String module, String operation,
                    Long recordId, LocalDateTime occurredAt,
                    AuditResult result, String detail) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        this.id = id;
        this.userId = userId == null
                ? null
                : ValidationUtil.requirePositiveId(userId, "usuario");
        this.module = ValidationUtil.requireText(
                module, "módulo", 60).toUpperCase(Locale.ROOT);
        this.operation = ValidationUtil.requireText(
                operation, "operación", 40).toUpperCase(Locale.ROOT);
        this.recordId = recordId == null
                ? null
                : ValidationUtil.requirePositiveId(recordId, "registro");
        this.occurredAt = ValidationUtil.requireNonNull(
                occurredAt, "fecha y hora");
        this.result = ValidationUtil.requireNonNull(
                result, "resultado");
        this.detail = ValidationUtil.optionalText(
                detail, "detalle", 500);
    }

    /** Called by the DAO after the INSERT. Only works while the id is still 0. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }
        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    public long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getModule() {
        return module;
    }

    public String getOperation() {
        return operation;
    }

    public Long getRecordId() {
        return recordId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public AuditResult getResult() {
        return result;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof AuditLog that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "AuditLog{id=" + id
                + ", module=" + module
                + ", operation=" + operation
                + ", result=" + result + "}";
    }
}