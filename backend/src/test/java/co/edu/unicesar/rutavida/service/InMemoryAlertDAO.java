
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.AlertDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Alert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake AlertDAO for tests, without Oracle.
 * It keeps copies, like a database.
 */
public class InMemoryAlertDAO implements AlertDAO {

    private static final Comparator<Alert> NEWEST_FIRST =
            Comparator.comparing(Alert::getCreatedAt)
                    .thenComparing(Alert::getId)
                    .reversed();

    private final Map<Long, Alert> saved = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public long insert(Alert alert) {
        long id = nextId++;
        alert.setId(id);
        saved.put(id, copy(alert));
        return id;
    }

    @Override
    public Optional<Alert> findById(long id) {
        return Optional.ofNullable(saved.get(id))
                .map(InMemoryAlertDAO::copy);
    }

    @Override
    public List<Alert> findByUser(long userId, int limit) {
        return saved.values().stream()
                .filter(alert -> alert.getUserId() == userId)
                .sorted(NEWEST_FIRST)
                .limit(limit)
                .map(InMemoryAlertDAO::copy)
                .toList();
    }

    @Override
    public List<Alert> findUnreadByUser(long userId) {
        return saved.values().stream()
                .filter(alert -> alert.getUserId() == userId && !alert.isRead())
                .sorted(NEWEST_FIRST)
                .map(InMemoryAlertDAO::copy)
                .toList();
    }

    @Override
    public void markAsRead(long id) {
        Alert alert = saved.get(id);

        if (alert == null) {
            throw new NotFoundException("Alerta", id);
        }

        alert.setRead(true);
    }

    /** Every alert saved, in insertion order. */
    public List<Alert> all() {
        return new ArrayList<>(saved.values());
    }

    private static Alert copy(Alert source) {
        return new Alert(
                source.getId(),
                source.getEmergencyId(),
                source.getUserId(),
                source.getType(),
                source.getMessage(),
                source.getCreatedAt(),
                source.isRead()
        );
    }
}
