package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.enums.CodedEnum;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class of every JdbcXxxDAO (GoF Template Method): it gets the connection, binds the
 * parameters, runs the SQL and turns SQLException into DataAccessException. A concrete DAO
 * only writes its SQL and its RowMapper.
 *
 * <p>Rules for subclasses: always use {@code ?} parameters (never concatenate values into
 * the SQL) and never call {@code commit()} or {@code close()} on a connection: the
 * TransactionManager owns the transaction.
 *
 * <p>Parameter conversion done by {@link #bind}: {@code null} → NULL, {@link LocalDateTime}
 * → TIMESTAMP, {@link LocalDate} → DATE, {@link Boolean} → 'S'/'N', {@link CodedEnum} →
 * its Spanish code (e.g. Severity.HIGH → 'ALTA'), anything else → setObject.
 */
public abstract class AbstractJdbcDAO {

    protected final ConnectionProvider connections;

    protected AbstractJdbcDAO(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** Runs a SELECT and maps every row. Returns an empty list when nothing matches. */
    protected <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet rows = statement.executeQuery()) {
                    List<T> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(mapper.map(rows));
                    }
                    return result;
                }
            }
        });
    }

    /**
     * Runs a SELECT that returns zero or one row. More than one row is a programming error.
     * A row whose mapper returns null also gives an empty Optional.
     */
    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = queryList(sql, mapper, params);
        if (rows.size() > 1) {
            throw new DataAccessException("Expected at most one row but got " + rows.size(), null);
        }
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    /** Runs an INSERT, UPDATE or DELETE and returns the number of affected rows. */
    protected int execute(String sql, Object... params) {
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Runs an INSERT whose id comes from the sequence DEFAULT of {@code idColumn}
     * (e.g. "id_emergencia") and returns the generated id.
     */
    protected long insertReturningId(String sql, String idColumn, Object... params) {
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, new String[]{idColumn})) {
                bind(statement, params);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new DataAccessException("The database did not return the generated " + idColumn, null);
                    }
                    return keys.getLong(1);
                }
            }
        });
    }

    /** Binds the parameters in order (index 1, 2, 3…), converting the types listed above. */
    protected static void bind(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            int index = i + 1;
            Object value = params[i];
            if (value == null) {
                statement.setNull(index, Types.VARCHAR);
            } else if (value instanceof LocalDateTime dateTime) {
                statement.setTimestamp(index, Timestamp.valueOf(dateTime));
            } else if (value instanceof LocalDate date) {
                statement.setDate(index, java.sql.Date.valueOf(date));
            } else if (value instanceof Boolean flag) {
                statement.setString(index, flag ? "S" : "N");
            } else if (value instanceof CodedEnum coded) {
                statement.setString(index, coded.code());
            } else {
                statement.setObject(index, value);
            }
        }
    }

    /** Reads a TIMESTAMP column; null stays null. */
    protected static LocalDateTime getLocalDateTime(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    /** Reads a nullable NUMBER column as Long (getLong would turn NULL into 0). */
    protected static Long getNullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    /** Reads a nullable NUMBER column as Integer (getInt would turn NULL into 0). */
    protected static Integer getNullableInt(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    /** Reads an S/N flag column: 'S' → true, anything else → false. */
    protected static boolean getFlag(ResultSet row, String column) throws SQLException {
        return "S".equals(row.getString(column));
    }
}