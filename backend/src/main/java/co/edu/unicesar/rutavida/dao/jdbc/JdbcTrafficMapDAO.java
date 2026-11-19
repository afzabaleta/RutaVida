package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.TrafficMapDAO;
import co.edu.unicesar.rutavida.dto.TrafficSegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * TrafficMapDAO on Oracle: the last row of TRAFICO of each segment (ROW_NUMBER, the same
 * rule as the dashboard in JdbcDashboardDAO), joined with TRAMO_VIAL and the NODO_VIAL of
 * each end.
 */
public class JdbcTrafficMapDAO extends AbstractJdbcDAO implements TrafficMapDAO {

    private static final String CONGESTED = """
            SELECT u.id_tramo, t.nombre_via, u.nivel, u.factor_congestion, u.fecha_hora, u.fuente,
                   a.latitud AS lat_a, a.longitud AS lon_a, b.latitud AS lat_b, b.longitud AS lon_b
              FROM (SELECT r.id_tramo, r.nivel, r.factor_congestion, r.fecha_hora, r.fuente,
                           ROW_NUMBER() OVER (PARTITION BY r.id_tramo
                                              ORDER BY r.fecha_hora DESC, r.id_trafico DESC) AS orden
                      FROM trafico r
                     WHERE r.fecha_hora >= ?) u
              JOIN tramo_vial t ON t.id_tramo = u.id_tramo
              JOIN nodo_vial a ON a.id_nodo = t.id_nodo_origen
              JOIN nodo_vial b ON b.id_nodo = t.id_nodo_destino
             WHERE u.orden = 1 AND u.nivel <> 'FLUIDO'
             ORDER BY u.factor_congestion DESC, u.id_tramo""";

    private static final RowMapper<TrafficSegmentView> MAPPER = row -> new TrafficSegmentView(
            row.getLong("id_tramo"),
            row.getString("nombre_via"),
            TrafficLevel.fromCode(row.getString("nivel")),
            row.getDouble("factor_congestion"),
            getLocalDateTime(row, "fecha_hora"),
            TrafficSource.fromCode(row.getString("fuente")),
            new GeoPoint(row.getDouble("lat_a"), row.getDouble("lon_a")),
            new GeoPoint(row.getDouble("lat_b"), row.getDouble("lon_b")));

    public JdbcTrafficMapDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public List<TrafficSegmentView> congestedSince(LocalDateTime since) {
        Objects.requireNonNull(since, "since");
        return queryList(CONGESTED, MAPPER, since);
    }
}
