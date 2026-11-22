package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.TrafficReading;

import java.util.List;

/**
 * Traffic history (table TRAFICO). The current traffic lives in memory, in the graph;
 * this table only keeps the measurements for reports, so it is append-only.
 */
public interface TrafficReadingDAO {

    /**
     * Saves many new readings (id 0) with few round trips to Oracle. The generated ids
     * are not assigned to the objects: history rows are not read back one by one.
     * Call it inside a transaction so that a failed batch saves nothing.
     */
    void insertBatch(List<TrafficReading> readings);
}