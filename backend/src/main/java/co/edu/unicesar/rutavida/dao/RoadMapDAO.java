package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.dto.SegmentView;

import java.util.Collection;
import java.util.List;

/**
 * Read-only views of the road network for the map (TRAMO_VIAL with the coordinates of its
 * two NODO_VIAL). Like DashboardDAO, it returns DTOs because no entity holds a segment
 * together with its coordinates.
 */
public interface RoadMapDAO {

    /** The segments with those ids that exist, in any order. An empty collection gives an empty list. */
    List<SegmentView> segmentViews(Collection<Long> segmentIds);
}