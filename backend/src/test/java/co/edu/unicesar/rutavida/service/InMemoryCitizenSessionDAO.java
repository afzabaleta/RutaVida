package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.CitizenSessionDAO;
import co.edu.unicesar.rutavida.model.CitizenSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fake CitizenSessionDAO for tests, without Oracle (DT-13). Like the table, a revoked session
 * stays saved with revoked = true.
 */
public class InMemoryCitizenSessionDAO implements CitizenSessionDAO {

    private final List<CitizenSession> saved = new ArrayList<>();
    private long nextId = 1;

    @Override
    public long insert(CitizenSession session) {
        long id = nextId++;
        session.setId(id);
        saved.add(session);
        return id;
    }

    @Override
    public Optional<CitizenSession> findValid(String tokenHash, LocalDateTime now) {
        return saved.stream()
                .filter(each -> each.getTokenHash().equals(tokenHash))
                .filter(each -> !each.isRevoked() && each.getExpiresAt().isAfter(now))
                .findFirst();
    }

    @Override
    public int revoke(String tokenHash) {
        return revokeWhere(each -> each.getTokenHash().equals(tokenHash));
    }

    @Override
    public int revokeAllOf(long userId) {
        return revokeWhere(each -> each.getUserId() == userId);
    }

    /** Every session saved, revoked or not (for assertions). */
    public List<CitizenSession> all() {
        return List.copyOf(saved);
    }

    private int revokeWhere(java.util.function.Predicate<CitizenSession> which) {
        int count = 0;
        for (int i = 0; i < saved.size(); i++) {
            CitizenSession each = saved.get(i);
            if (!each.isRevoked() && which.test(each)) {
                saved.set(i, new CitizenSession(each.getId(), each.getUserId(), each.getTokenHash(),
                        each.getCreatedAt(), each.getExpiresAt(), true));
                count++;
            }
        }
        return count;
    }
}
