package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.RouteDAO;
import co.edu.unicesar.rutavida.exception.NotFoundException;
import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * RouteDAO on Oracle (tables RUTA and RUTA_TRAMO). A route is saved with its segments in
 * one transaction, and read with two queries in total (routes, then all their segments),
 * like JdbcRoleDAO, not one query per route.
 */
public class JdbcRouteDAO extends AbstractJdbcDAO implements RouteDAO {

    private static final String SELECT_ROUTE =
            "SELECT id_ruta, id_emergencia, id_vehiculo, id_hospital_destino, tipo_ruta, distancia_km,"
                    + " tiempo_estimado_min, costo_total, fecha_generacion, es_recomendada, estado FROM ruta";

    private static final String SELECT_SEGMENTS =
            "SELECT id_ruta, orden_tramo, id_tramo, tiempo_estimado_seg, factor_congestion, semaforos_afectados"
                    + " FROM ruta_tramo WHERE id_ruta IN (SELECT id_ruta FROM ruta WHERE %s)"
                    + " ORDER BY id_ruta, orden_tramo";

    private static final String INSERT_ROUTE =
            "INSERT INTO ruta (id_emergencia, id_vehiculo, id_hospital_destino, tipo_ruta, distancia_km,"
                    + " tiempo_estimado_min, costo_total, fecha_generacion, es_recomendada, estado)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SEGMENT =
            "INSERT INTO ruta_tramo (id_ruta, orden_tramo, id_tramo, tiempo_estimado_seg,"
                    + " factor_congestion, semaforos_afectados) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS = "UPDATE ruta SET estado = ? WHERE id_ruta = ?";

    /** Columns of RUTA before the segments are attached. */
    private record RouteRow(long id, long emergencyId, long vehicleId, Long hospitalId, RouteType type,
                            double distanceKm, double minutes, double cost, LocalDateTime generatedAt,
                            boolean recommended, RouteStatus status) {
    }

    /** One row of RUTA_TRAMO with the route it belongs to. */
    private record SegmentRow(long routeId, RouteSegment segment) {
    }

    private static final RowMapper<RouteRow> ROUTE_MAPPER = row -> new RouteRow(
            row.getLong("id_ruta"),
            row.getLong("id_emergencia"),
            row.getLong("id_vehiculo"),
            getNullableLong(row, "id_hospital_destino"),
            RouteType.fromCode(row.getString("tipo_ruta")),
            row.getDouble("distancia_km"),
            row.getDouble("tiempo_estimado_min"),
            row.getDouble("costo_total"),
            getLocalDateTime(row, "fecha_generacion"),
            getFlag(row, "es_recomendada"),
            RouteStatus.fromCode(row.getString("estado")));

    private static final RowMapper<SegmentRow> SEGMENT_MAPPER = row -> new SegmentRow(
            row.getLong("id_ruta"),
            new RouteSegment(
                    row.getInt("orden_tramo"),
                    row.getLong("id_tramo"),
                    getNullableDouble(row, "tiempo_estimado_seg"),
                    getNullableDouble(row, "factor_congestion"),
                    getNullableInt(row, "semaforos_afectados")));


    public JdbcRouteDAO(JdbcTransactionManager connections) {
        super(connections);
    }

    @Override
    public long insert(Route route) {
        Objects.requireNonNull(route, "route");
        if (route.getId() != 0) {
            throw new IllegalArgumentException("The route is already saved with id " + route.getId());
        }
        long id = connections.callInTransaction(() -> {
            long routeId = insertReturningId(INSERT_ROUTE, "id_ruta",
                    route.getEmergencyId(), route.getVehicleId(), route.getDestinationHospitalId(),
                    route.getType(), route.getDistanceKm(), route.getEstimatedMinutes(), route.getTotalCost(),
                    route.getGeneratedAt(), route.isRecommended(), route.getStatus());
            insertSegments(routeId, route.getSegments());
            return routeId;
        });
        route.setId(id);
        return id;
    }

    /** One batch for all the segments; the same SQL type in each position for every row. */
    private void insertSegments(long routeId, List<RouteSegment> segments) {
        connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SEGMENT)) {
                for (RouteSegment segment : segments) {
                    statement.setLong(1, routeId);
                    statement.setInt(2, segment.order());
                    statement.setLong(3, segment.segmentId());
                    setNullableDecimal(statement, 4, segment.estimatedSeconds());
                    setNullableDecimal(statement, 5, segment.congestionFactor());
                    if (segment.trafficLightCount() == null) {
                        statement.setNull(6, Types.NUMERIC);
                    } else {
                        statement.setInt(6, segment.trafficLightCount());
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
                return null;
            }
        });
    }

    @Override
    public Optional<Route> findById(long id) {
        return find("id_ruta = ?", "", id).stream().findFirst();
    }

    @Override
    public List<Route> findByEmergency(long emergencyId) {
        return find("id_emergencia = ?", " ORDER BY fecha_generacion, id_ruta", emergencyId);
    }

    @Override
    public List<Route> findActive() {
        return find("estado = ?", " ORDER BY fecha_generacion, id_ruta", RouteStatus.ACTIVE);
    }

    /** Active routes are few (one per unit on the move), so they are filtered in Java. */
    @Override
    public List<Route> findActiveUsingSegments(Set<Long> segmentIds) {
        Objects.requireNonNull(segmentIds, "segmentIds");
        if (segmentIds.isEmpty()) {
            return List.of();
        }
        return findActive().stream()
                .filter(route -> route.getSegments().stream().anyMatch(step -> segmentIds.contains(step.segmentId())))
                .toList();
    }

    @Override
    public void updateStatus(long id, RouteStatus status) {
        Objects.requireNonNull(status, "status");
        int rows = execute(UPDATE_STATUS, status, id);
        if (rows == 0) {
            throw new NotFoundException("Ruta", id);
        }
    }

    /** Routes that match {@code where}, each with its segments (two queries in total). */
    private List<Route> find(String where, String orderBy, Object param) {
        List<RouteRow> routes = queryList(SELECT_ROUTE + " WHERE " + where + orderBy, ROUTE_MAPPER, param);
        if (routes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RouteSegment>> segments = new HashMap<>();
        for (SegmentRow row : queryList(String.format(SELECT_SEGMENTS, where), SEGMENT_MAPPER, param)) {
            segments.computeIfAbsent(row.routeId(), key -> new ArrayList<>()).add(row.segment());
        }
        return routes.stream()
                .map(route -> new Route(route.id(), route.emergencyId(), route.vehicleId(), route.hospitalId(),
                        route.type(), route.distanceKm(), route.minutes(), route.cost(), route.generatedAt(),
                        route.recommended(), route.status(), segments.getOrDefault(route.id(), List.of())))
                .toList();
    }

    private static void setNullableDecimal(PreparedStatement statement, int index, Double value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setBigDecimal(index, BigDecimal.valueOf(value));
        }
    }

    /** Reads a nullable NUMBER as Double (getDouble would turn NULL into 0). */
    private static Double getNullableDouble(ResultSet row, String column) throws SQLException {
        double value = row.getDouble(column);
        return row.wasNull() ? null : value;
    }
}