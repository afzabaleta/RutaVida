package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.VehicleLocationDAO;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.VehicleLocation;
import co.edu.unicesar.rutavida.model.enums.PositionSource;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * VehicleLocationDAO on Oracle (table UBICACION_VEHICULO). Same JDBC batch as
 * JdbcTrafficReadingDAO: rows go to Oracle in groups of {@link #BATCH_SIZE}.
 */
public class JdbcVehicleLocationDAO extends AbstractJdbcDAO implements VehicleLocationDAO {

    /** Rows sent to Oracle in each executeBatch. */
    static final int BATCH_SIZE = 500;

    private static final String INSERT =
            "INSERT INTO ubicacion_vehiculo (id_vehiculo, fecha_hora, latitud, longitud, velocidad_kmh,"
                    + " direccion_grados, fuente) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_VEHICLE =
            "SELECT id_ubicacion, id_vehiculo, fecha_hora, latitud, longitud, velocidad_kmh, direccion_grados,"
                    + " fuente FROM ubicacion_vehiculo WHERE id_vehiculo = ? AND fecha_hora BETWEEN ? AND ?"
                    + " ORDER BY fecha_hora, id_ubicacion";

    private static final RowMapper<VehicleLocation> MAPPER = row -> new VehicleLocation(
            row.getLong("id_ubicacion"),
            row.getLong("id_vehiculo"),
            getLocalDateTime(row, "fecha_hora"),
            new GeoPoint(row.getDouble("latitud"), row.getDouble("longitud")),
            getNullableDouble(row, "velocidad_kmh"),
            getNullableDouble(row, "direccion_grados"),
            PositionSource.fromCode(row.getString("fuente")));

    public JdbcVehicleLocationDAO(ConnectionProvider connections) {
        super(connections);
    }

    @Override
    public void insertBatch(List<VehicleLocation> locations) {
        Objects.requireNonNull(locations, "locations");
        for (VehicleLocation location : locations) {
            Objects.requireNonNull(location, "location");
            if (location.getId() != 0) {
                throw new IllegalArgumentException("The position is already saved with id " + location.getId());
            }
        }
        if (locations.isEmpty()) {
            return;
        }

        connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                int pending = 0;
                for (VehicleLocation location : locations) {
                    bindLocation(statement, location);
                    statement.addBatch();
                    pending++;
                    if (pending == BATCH_SIZE) {
                        statement.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    statement.executeBatch();
                }
                return null;
            }
        });
    }

    @Override
    public List<VehicleLocation> findByVehicle(long vehicleId, LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return queryList(SELECT_BY_VEHICLE, MAPPER, vehicleId, from, to);
    }

    private static void bindLocation(PreparedStatement statement, VehicleLocation location) throws SQLException {
        statement.setLong(1, location.getVehicleId());
        statement.setTimestamp(2, Timestamp.valueOf(location.getRecordedAt()));
        statement.setBigDecimal(3, BigDecimal.valueOf(location.getLocation().latitude()));
        statement.setBigDecimal(4, BigDecimal.valueOf(location.getLocation().longitude()));
        setNullableDecimal(statement, 5, location.getSpeedKmh());
        setNullableDecimal(statement, 6, location.getHeadingDegrees());
        statement.setString(7, location.getSource().code());
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