package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.RoadMapDAO;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** RoadMapDAO on Oracle: TRAMO_VIAL joined with the NODO_VIAL of each end. */
public class JdbcRoadMapDAO extends AbstractJdbcDAO implements RoadMapDAO {

    private static final String SEGMENTS =
            "SELECT t.id_tramo, t.nombre_via, a.latitud AS lat_a, a.longitud AS lon_a,"
                    + " b.latitud AS lat_b, b.longitud AS lon_b"
                    + " FROM tramo_vial t"
                    + " JOIN nodo_vial a ON a.id_nodo = t.id_nodo_origen"
                    + " JOIN nodo_vial b ON b.id_nodo = t.id_nodo_destino"
                    + " WHERE t.id_tramo IN (";

    /** Oracle accepts at most 1000 values in one IN list. */
    private static final int MAX_IN = 1000;

    private static final RowMapper<SegmentView> MAPPER = row -> new SegmentView(
            row.getLong("id_tramo"),
            row.getString("nombre_via"),
            new GeoPoint(row.getDouble("lat_a"), row.getDouble("lon_a")),
            new GeoPoint(row.getDouble("lat_b"), row.getDouble("lon_b")));

    public JdbcRoadMapDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public List<SegmentView> segmentViews(Collection<Long> segmentIds) {
        Objects.requireNonNull(segmentIds, "segmentIds");
        List<Long> ids = segmentIds.stream().distinct().toList();
        List<SegmentView> views = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += MAX_IN) {
            List<Long> part = ids.subList(start, Math.min(start + MAX_IN, ids.size()));
            String marks = String.join(", ", Collections.nCopies(part.size(), "?"));
            views.addAll(queryList(SEGMENTS + marks + ")", MAPPER, part.toArray()));
        }
        return views;
    }
}