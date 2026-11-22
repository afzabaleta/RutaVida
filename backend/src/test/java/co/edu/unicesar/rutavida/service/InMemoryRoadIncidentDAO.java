package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dao.RoadIncidentDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake RoadIncidentDAO for tests, without Oracle.
 * It keeps copies, like a database: a change made to an incident
 * is only saved when update() is called.
 */
public class InMemoryRoadIncidentDAO implements RoadIncidentDAO {

    private final Map<Long, RoadIncident> saved = new LinkedHashMap<>();
    private long nextId = 1;

    public int updates;

    @Override
    public long insert(RoadIncident incident) {
        long id = nextId++;
        incident.setId(id);
        saved.put(id, copy(incident));
        return id;
    }

    @Override
    public Optional<RoadIncident> findById(long id) {
        return Optional.ofNullable(saved.get(id))
                .map(InMemoryRoadIncidentDAO::copy);
    }

    @Override
    public List<RoadIncident> findByStatus(IncidentStatus status) {
        return saved.values().stream()
                .filter(incident -> incident.getStatus() == status)
                .sorted(Comparator.comparing(RoadIncident::getStartedAt))
                .map(InMemoryRoadIncidentDAO::copy)
                .toList();
    }

    @Override
    public void update(RoadIncident incident) {
        if (!saved.containsKey(incident.getId())) {
            throw new NotFoundException("Incidente vial", incident.getId());
        }

        updates++;
        saved.put(incident.getId(), copy(incident));
    }

    /** What is saved now, without going through the service. */
    public RoadIncident stored(long id) {
        return copy(saved.get(id));
    }

    private static RoadIncident copy(RoadIncident source) {
        return new RoadIncident(
                source.getId(),
                source.getSegmentId(),
                source.getType(),
                source.getSeverity(),
                source.isBlocksSegment(),
                source.getDescription(),
                source.getStartedAt(),
                source.getEndedAt(),
                source.getStatus(),
                source.getOrigin()
        );
    }
}