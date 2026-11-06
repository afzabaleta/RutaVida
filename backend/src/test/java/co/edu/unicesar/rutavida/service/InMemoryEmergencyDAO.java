
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Emergency;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake EmergencyDAO for service tests: keeps emergencies in memory without Oracle. */
public class InMemoryEmergencyDAO implements EmergencyDAO {

    public final Map<Long, Emergency> saved = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public long insert(Emergency emergency) {
        long id = nextId++;
        emergency.setId(id);
        saved.put(id, emergency);
        return id;
    }

    @Override
    public Optional<Emergency> findById(long id) {
        return Optional.ofNullable(saved.get(id));
    }

    @Override
    public List<Emergency> findActive() {
        return saved.values().stream()
                .filter(e -> e.getStatus() != EmergencyStatus.CLOSED)
                .sorted(Comparator.comparing(Emergency::getReportedAt).reversed())
                .toList();
    }

    @Override
    public List<Emergency> findByPeriod(LocalDateTime from, LocalDateTime to) {
        return saved.values().stream()
                .filter(e -> !e.getReportedAt().isBefore(from)
                        && !e.getReportedAt().isAfter(to))
                .sorted(Comparator.comparing(Emergency::getReportedAt))
                .toList();
    }

    @Override
    public void updateStatus(long id, EmergencyStatus status, LocalDateTime closedAt) {
        Emergency emergency = findById(id)
                .orElseThrow(() -> new NotFoundException("Emergencia", id));
        emergency.setStatus(status);
        emergency.setClosedAt(closedAt);
    }
}
