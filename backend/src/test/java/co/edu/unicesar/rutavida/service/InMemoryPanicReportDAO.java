package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.PanicReportDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.PanicReport;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake PanicReportDAO for tests, without Oracle (for the panic button service, S11).
 * It keeps copies, like a database.
 */
public class InMemoryPanicReportDAO implements PanicReportDAO {

    private static final Comparator<PanicReport> OLDEST_FIRST =
            Comparator.comparing(PanicReport::getReportedAt).thenComparing(PanicReport::getId);

    private final Map<Long, PanicReport> saved = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public long insert(PanicReport report) {
        long id = nextId++;
        report.setId(id);
        saved.put(id, copy(report));
        return id;
    }

    @Override
    public Optional<PanicReport> findById(long id) {
        return Optional.ofNullable(saved.get(id)).map(InMemoryPanicReportDAO::copy);
    }

    @Override
    public List<PanicReport> findPendingSince(LocalDateTime since) {
        return saved.values().stream()
                .filter(r -> r.getStatus() == PanicReportStatus.PENDING && !r.getReportedAt().isBefore(since))
                .sorted(OLDEST_FIRST)
                .map(InMemoryPanicReportDAO::copy)
                .toList();
    }

    @Override
    public List<PanicReport> findSince(LocalDateTime since) {
        return saved.values().stream()
                .filter(r -> !r.getReportedAt().isBefore(since))
                .sorted(OLDEST_FIRST.reversed())
                .map(InMemoryPanicReportDAO::copy)
                .toList();
    }

    @Override
    public int countByDeviceSince(String deviceId, LocalDateTime since) {
        return (int) saved.values().stream()
                .filter(r -> r.getDeviceId().equals(deviceId) && !r.getReportedAt().isBefore(since))
                .count();
    }

    @Override
    public int countByUserSince(long userId, LocalDateTime since) {
        return (int) saved.values().stream()
                .filter(r -> r.getUserId() != null && r.getUserId() == userId && !r.getReportedAt().isBefore(since))
                .count();
    }

    @Override
    public int countByUserAndStatusSince(long userId, PanicReportStatus status, LocalDateTime since) {
        return (int) saved.values().stream()
                .filter(r -> r.getUserId() != null && r.getUserId() == userId && r.getStatus() == status
                        && !r.getReportedAt().isBefore(since))
                .count();
    }

    @Override
    public void markValidated(Collection<Long> ids, long emergencyId) {
        for (Long id : ids) {
            PanicReport report = saved.get(id);
            if (report == null) {
                throw new NotFoundException("Reporte de pánico", id);
            }
            report.setStatus(PanicReportStatus.VALIDATED);
            report.setEmergencyId(emergencyId);
        }
    }

    @Override
    public void updateStatus(long id, PanicReportStatus status) {
        if (status == PanicReportStatus.VALIDATED) {
            throw new IllegalArgumentException("A validated report needs its emergency: use markValidated");
        }
        PanicReport report = saved.get(id);
        if (report == null) {
            throw new NotFoundException("Reporte de pánico", id);
        }
        report.setStatus(status);
        report.setEmergencyId(null);
    }

    @Override
    public int expirePendingBefore(LocalDateTime before) {
        int expired = 0;
        for (PanicReport report : saved.values()) {
            if (report.getStatus() == PanicReportStatus.PENDING && report.getReportedAt().isBefore(before)) {
                report.setStatus(PanicReportStatus.EXPIRED);
                expired++;
            }
        }
        return expired;
    }

    private static PanicReport copy(PanicReport r) {
        return new PanicReport(r.getId(), r.getLocation(), r.getReportedAt(), r.getDeviceId(), r.getUserId(),
                r.getEmergencyTypeId(), r.getDescription(), r.getStatus(), r.getEmergencyId());
    }
}
