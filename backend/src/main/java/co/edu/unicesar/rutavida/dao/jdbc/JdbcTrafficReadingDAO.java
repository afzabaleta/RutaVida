package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.TrafficReadingDAO;
import co.edu.unicesar.rutavida.model.TrafficReading;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;

/**
 * TrafficReadingDAO on Oracle (table TRAFICO). Uses a JDBC batch: the rows travel to
 * Oracle in groups of {@link #BATCH_SIZE} instead of one INSERT per round trip.
 *
 * <p>The parameters are bound by hand, not with {@code bind}: in a batch every row must
 * use the same SQL type in each position, also when the speed is NULL.
 */
public class JdbcTrafficReadingDAO extends AbstractJdbcDAO implements TrafficReadingDAO {

    /** Rows sent to Oracle in each executeBatch. */
    static final int BATCH_SIZE = 500;

    private static final String INSERT =
            "INSERT INTO trafico (id_tramo, fecha_hora, nivel, velocidad_promedio_kmh,"
                    + " factor_congestion, fuente) VALUES (?, ?, ?, ?, ?, ?)";

    public JdbcTrafficReadingDAO(ConnectionProvider connections) {
        super(connections);
    }

    @Override
    public void insertBatch(List<TrafficReading> readings) {
        Objects.requireNonNull(readings, "readings");
        for (TrafficReading reading : readings) {
            Objects.requireNonNull(reading, "reading");
            if (reading.getId() != 0) {
                throw new IllegalArgumentException("The reading is already saved with id " + reading.getId());
            }
        }
        if (readings.isEmpty()) {
            return;
        }

        connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                int pending = 0;
                for (TrafficReading reading : readings) {
                    bindReading(statement, reading);
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

    private static void bindReading(PreparedStatement statement, TrafficReading reading) throws SQLException {
        statement.setLong(1, reading.getSegmentId());
        statement.setTimestamp(2, Timestamp.valueOf(reading.getMeasuredAt()));
        statement.setString(3, reading.getLevel().code());
        if (reading.getAverageSpeedKmh() == null) {
            statement.setNull(4, Types.NUMERIC);
        } else {
            statement.setBigDecimal(4, BigDecimal.valueOf(reading.getAverageSpeedKmh()));
        }
        statement.setBigDecimal(5, BigDecimal.valueOf(reading.getCongestionFactor()));
        statement.setString(6, reading.getSource().code());
    }
}