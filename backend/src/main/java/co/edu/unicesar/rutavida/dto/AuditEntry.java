package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.enums.AuditResult;

import java.time.LocalDateTime;

/**
 * One line of the audit as the administration screen shows it (docs/api.md 15.13): the entry
 * of AUDITORIA plus the username, so the screen does not have to look it up.
 *
 * @param username null when nobody was logged in (for example a failed login of an unknown user)
 */
public record AuditEntry(
        long id,
        LocalDateTime occurredAt,
        Long userId,
        String username,
        String module,
        String operation,
        Long recordId,
        AuditResult result,
        String detail
) {

    public static AuditEntry of(AuditLog log, String username) {
        return new AuditEntry(log.getId(), log.getOccurredAt(), log.getUserId(), username, log.getModule(),
                log.getOperation(), log.getRecordId(), log.getResult(), log.getDetail());
    }
}
