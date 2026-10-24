package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.RoadSegmentDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.RoadSegment;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;

import java.util.List;
import java.util.Objects;

/**
 * RoadSegmentDAO on Oracle (table TRAMO_VIAL). The virtual column tiempo_base_seg is not
 * read: RoadSegment.getBaseTimeSeconds() uses the same formula.
 */
public class JdbcRoadSegmentDAO extends AbstractJdbcDAO implements RoadSegmentDAO {

    private static final String SELECT_ALL =
            "SELECT id_tramo, id_nodo_origen, id_nodo_destino, osm_way_id, nombre_via,"
                    + " distancia_m, velocidad_max_kmh, estado"
                    + " FROM tramo_vial ORDER BY id_tramo";

    private static final String UPDATE_STATUS =
            "UPDATE tramo_vial SET estado = ? WHERE id_tramo = ?";

    private static final RowMapper<RoadSegment> MAPPER = row -> new RoadSegment(
            row.getLong("id_tramo"),
            row.getLong("id_nodo_origen"),
            row.getLong("id_nodo_destino"),
            getNullableLong(row, "osm_way_id"),
            row.getString("nombre_via"),
            row.getDouble("distancia_m"),
            row.getDouble("velocidad_max_kmh"),
            RoadSegmentStatus.fromCode(row.getString("estado")));

    public JdbcRoadSegmentDAO(ConnectionProvider connections) {
        super(connections);
    }

    @Override
    public List<RoadSegment> findAll() {
        return queryList(SELECT_ALL, MAPPER);
    }

    @Override
    public void updateStatus(long id, RoadSegmentStatus status) {
        Objects.requireNonNull(status, "status");
        int rows = execute(UPDATE_STATUS, status, id);
        if (rows == 0) {
            throw new NotFoundException("Tramo vial", id);
        }
    }
}