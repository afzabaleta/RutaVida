
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AuditDAO;
import co.edu.unicesar.rutavida.model.AuditLog;
import co.edu.unicesar.rutavida.model.enums.AuditResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fake AuditDAO for service tests:
 * keeps audit entries in memory, without Oracle.
 */
public class InMemoryAuditDAO implements AuditDAO {


    public final List<AuditLog> saved = new ArrayList<>();


    @Override
    public void insert(AuditLog log) {
        log.setId(saved.size() + 1L);
        saved.add(log);
    }

    @Override
    public List<AuditLog> findByRecord(String module, long recordId) {
        return saved.stream()
                .filter(log ->
                        log.getModule().equals(
                                module.toUpperCase(Locale.ROOT)))
                .filter(log ->
                        log.getRecordId() != null
                                && log.getRecordId() == recordId)
                .toList();
    }

    @Override
    public List<AuditLog> findByPeriod(
            LocalDateTime from, LocalDateTime to) {

        return saved.stream()
                .filter(log ->
                        !log.getOccurredAt().isBefore(from)
                                && !log.getOccurredAt().isAfter(to))
                .toList();
    }


    @Override
    public List<AuditLog> search(LocalDateTime from, LocalDateTime to, String module, Long userId,
                                 AuditResult result, int limit) {
        return saved.stream()
                .filter(log -> !log.getOccurredAt().isBefore(from) && !log.getOccurredAt().isAfter(to))
                .filter(log -> module == null || log.getModule().equals(module))
                .filter(log -> userId == null || userId.equals(log.getUserId()))
                .filter(log -> result == null || log.getResult() == result)
                .sorted(java.util.Comparator.comparing(AuditLog::getOccurredAt)
                        .thenComparing(AuditLog::getId).reversed())
                .limit(limit)
                .toList();
    }

    public AuditLog last() {

        return saved.get(saved.size() - 1);
    }
}
