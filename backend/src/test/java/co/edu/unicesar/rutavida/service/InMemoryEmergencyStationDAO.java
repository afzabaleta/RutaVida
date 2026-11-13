
package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.EmergencyStationDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.EmergencyStation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake EmergencyStationDAO for service tests: keeps the stations in memory without Oracle. */
public class InMemoryEmergencyStationDAO implements EmergencyStationDAO {

    private final Map<Long, EmergencyStation> stations = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Optional<EmergencyStation> findById(long id) {
        return Optional.ofNullable(stations.get(id));
    }

    @Override
    public List<EmergencyStation> findAll() {
        return stations.values().stream()
                .sorted(Comparator.comparing(EmergencyStation::getName))
                .toList();
    }

    @Override
    public long insert(EmergencyStation station) {
        long id = nextId++;
        station.setId(id);
        stations.put(id, station);
        return id;
    }

    @Override
    public void update(EmergencyStation station) {
        if (!stations.containsKey(station.getId())) {
            throw new NotFoundException("Estación", station.getId());
        }
        stations.put(station.getId(), station);
    }
}
