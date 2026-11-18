package co.edu.unicesar.rutavida.dto;

import co.edu.unicesar.rutavida.model.enums.AuditResult;

import java.time.LocalDateTime;

/**
 * Filters of the audit search (CU-21, docs/api.md 15.13). Every field is optional:
 * without {@code from} and {@code to} it is the last 24 hours; without {@code limit}, 200.
 *
 * @param module    for example EMERGENCIAS or DESPACHO (any case)
 * @param userId    only what this user did
 * @param result    only EXITO or only FALLO
 * @param limit     at most this many entries, newest first (1 to 500)
 */
public record AuditQuery(
        LocalDateTime from,
        LocalDateTime to,
        String module,
        Long userId,
        AuditResult result,
        Integer limit
) {

    /** No filters: the last 24 hours. */
    public static AuditQuery recent() {
        return new AuditQuery(null, null, null, null, null, null);
    }
}
