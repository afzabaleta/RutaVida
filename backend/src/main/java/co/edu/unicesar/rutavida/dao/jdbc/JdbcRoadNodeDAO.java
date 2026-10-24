package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.RoadNodeDAO;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.RoadNode;
import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

import java.util.List;

/** RoadNodeDAO on Oracle (table NODO_VIAL). */
public class JdbcRoadNodeDAO extends AbstractJdbcDAO implements RoadNodeDAO {

    private static final String SELECT_ALL =
            "SELECT id_nodo, osm_id, referencia, latitud, longitud, tipo_nodo, estado"
                    + " FROM nodo_vial ORDER BY id_nodo";

    private static final RowMapper<RoadNode> MAPPER = row -> new RoadNode(
            row.getLong("id_nodo"),
            getNullableLong(row, "osm_id"),
            row.getString("referencia"),
            new GeoPoint(row.getDouble("latitud"), row.getDouble("longitud")),
            NodeType.fromCode(row.getString("tipo_nodo")),
            RecordStatus.fromCode(row.getString("estado")));

    public JdbcRoadNodeDAO(ConnectionProvider connections) {
        super(connections);
    }

    @Override
    public List<RoadNode> findAll() {
        return queryList(SELECT_ALL, MAPPER);
    }
}