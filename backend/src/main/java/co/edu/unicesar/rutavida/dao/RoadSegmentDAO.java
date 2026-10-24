package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.RoadSegment;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;

import java.util.List;

/**
 * Road segments (table TRAMO_VIAL). Each row is one driving direction: a two-way street
 * is stored as two segments (A→B and B→A). The segments come from the OpenStreetMap
 * script (K4); the application only reads them and changes their status.
 */
public interface RoadSegmentDAO {

    /** Every segment, whatever its status, ordered by id. */
    List<RoadSegment> findAll();

    /**
     * Changes the administrative status of one segment (ABIERTO, CERRADO, RESTRINGIDO),
     * e.g. when an active incident closes the street. Throws NotFoundException if the
     * segment does not exist.
     */
    void updateStatus(long id, RoadSegmentStatus status);
}