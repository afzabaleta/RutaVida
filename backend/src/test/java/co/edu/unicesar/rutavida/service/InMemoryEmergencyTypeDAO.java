
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.model.EmergencyType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake EmergencyTypeDAO for service tests: the catalog in memory. */
public class InMemoryEmergencyTypeDAO implements EmergencyTypeDAO {

    private final Map<Long, EmergencyType> types = new LinkedHashMap<>();

    public void add(EmergencyType type) {
        types.put(type.getId(), type);
    }

    @Override
    public Optional<EmergencyType> findById(long id) {
        return Optional.ofNullable(types.get(id));
    }

    @Override
    public List<EmergencyType> findAll() {
        return types.values().stream()
                .sorted(Comparator.comparingInt(EmergencyType::getPriority).reversed())
                .toList();
    }
}
