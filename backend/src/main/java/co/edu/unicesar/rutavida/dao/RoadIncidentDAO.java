package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;

import java.util.List;
import java.util.Optional;

/**
 * Road incidents (table INCIDENTE_VIAL). Every status change is audited by the trigger
 * trg_incidente_estado of 04_plsql.sql, not by this DAO.
 */
public interface RoadIncidentDAO {

    /** Saves a new incident (id 0), assigns the generated id to it and returns that id. */
    long insert(RoadIncident incident);

    Optional<RoadIncident> findById(long id);

    /** Incidents in that status, oldest first (e.g. the ACTIVE ones when the graph is loaded). */
    List<RoadIncident> findByStatus(IncidentStatus status);

    /**
     * Saves what can change in a saved incident: status, end date and whether it blocks
     * the segment. Throws NotFoundException if the incident does not exist.
     */
    void update(RoadIncident incident);
}