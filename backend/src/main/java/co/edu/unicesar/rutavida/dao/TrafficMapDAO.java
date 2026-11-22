package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.dto.TrafficSegmentView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads the current traffic for the map (docs/api.md 8.2). It is apart from
 * {@link TrafficReadingDAO}, which only writes: the simulation and the map use TRAFICO
 * in different ways.
 */
public interface TrafficMapDAO {

    /**
     * The last reading of each segment measured since {@code since}, only when that last
     * reading is MODERADO or CONGESTIONADO, with the street and both ends of the segment.
     * A segment whose last reading is FLUIDO is not returned. The most congested come first.
     */
    List<TrafficSegmentView> congestedSince(LocalDateTime since);
}
