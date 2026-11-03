package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.AuditResult;
import java.time.LocalDateTime;

/**
 * One entry of the audit trail (table AUDITORIA).
 * Only data: the business rules live in the service layer.
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

    public AuditLog(long id,
            Long userId,
            String module,
            String operation,
            Long recordId,
            LocalDateTime occurredAt,
            AuditResult result,
            String detail) {
        this.id = id;
        this.userId = userId;
        this.module = module;
        this.operation = operation;
        this.recordId = recordId;
        this.occurredAt = occurredAt;
        this.result = result;
        this.detail = detail;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
