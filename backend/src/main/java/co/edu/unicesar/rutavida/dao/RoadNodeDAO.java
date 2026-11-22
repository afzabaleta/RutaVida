package co.edu.unicesar.rutavida.dao;

import co.edu.unicesar.rutavida.model.RoadNode;

import java.util.List;

/**
 * Reading of the road network nodes (table NODO_VIAL). The network is loaded into Oracle
 * by the SQL script generated from OpenStreetMap (K4), so the application only reads it.
 */
public interface RoadNodeDAO {

    /** Every node, active and inactive, ordered by id. GraphLoader decides which ones it uses. */
    List<RoadNode> findAll();
}