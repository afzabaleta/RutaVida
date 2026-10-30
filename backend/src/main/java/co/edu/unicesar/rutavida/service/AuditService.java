
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AuditDAO;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.enums.AuditResult;

import co.edu.unicesar.rutavida.util.ValidationUtil;
import java.time.Clock;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Service responsible for recording and retrieving audit logs.
 */
public class AuditService {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final AuditDAO auditDao;
    private final Clock clock;

    public AuditService(AuditDAO auditDao, Clock clock) {
        this.auditDao = Objects.requireNonNull(
                auditDao, "auditDao");
        this.clock = Objects.requireNonNull(
                clock, "clock");
    }

    /**
     * Records an audited operation.
     */
    public void record(
            Long userId,
            String module,
            String operation,
            Long recordId,
            AuditResult result,
            String detail) {

        String safeDetail = detail;
        if (safeDetail != null
                && safeDetail.length() > MAX_DETAIL_LENGTH) {
            safeDetail = safeDetail.substring(
                    0, MAX_DETAIL_LENGTH);
        }
        AuditLog auditLog = new AuditLog(
                0,
                userId,
                ValidationUtil.requireText(module, "módulo", 60).toUpperCase(Locale.ROOT),
                ValidationUtil.requireText(operation, "operación", 40).toUpperCase(Locale.ROOT),
                recordId,
                LocalDateTime.now(clock),
                ValidationUtil.requireNonNull(result, "resultado"),
                safeDetail
        );
        auditDao.insert(auditLog);
    }

    /**
     * Finds audit logs associated with a record.
     */
    public List<AuditLog> findByRecord(
            String module, long recordId) {

        return auditDao.findByRecord(module, recordId);
    }
}
