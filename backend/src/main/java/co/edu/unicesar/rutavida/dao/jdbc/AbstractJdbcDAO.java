package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.BusinessRuleException;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.enums.CodedEnum;

import java.sql.CallableStatement;
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
 *
 * <p>Stored procedures are called with {@link #callProcedure} and
 * {@link #callProcedureReturningLong}. A PL/SQL function needs no special method: it is
 * read with a SELECT, e.g. {@code queryOne("SELECT fn_tiempo_respuesta(?) FROM dual", ...)}.
 */
public abstract class AbstractJdbcDAO {

    /** Oracle leaves ORA-20000 … ORA-20999 for RAISE_APPLICATION_ERROR (see 04_plsql.sql). */
    private static final int FIRST_APPLICATION_ERROR = 20000;
    private static final int LAST_APPLICATION_ERROR = 20999;

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

    /**
     * Calls a stored procedure that has only IN parameters, e.g.
     * {@code callProcedure("{call sp_cerrar_emergencia(?, ?)}", emergencyId, closedAt)}.
     * The parameters are converted like in {@link #bind}. The procedure does not commit:
     * the TransactionManager does.
     *
     * <p>An error raised by the procedure with RAISE_APPLICATION_ERROR is a broken business
     * rule, so it becomes a {@link BusinessRuleException} with the procedure's own message
     * (e.g. "La unidad 5 no esta disponible (OCUPADO)"). Any other SQL error becomes a
     * DataAccessException.
     */
    protected void callProcedure(String call, Object... params) {
        connections.withConnection(connection -> {
            try (CallableStatement statement = connection.prepareCall(call)) {
                bind(statement, params);
                statement.execute();
                return null;
            } catch (SQLException e) {
                rejectIfApplicationError(e);
                throw e;
            }
        });
    }

    /**
     * Calls a stored procedure whose LAST parameter is a NUMBER OUT and returns that value.
     * Pass only the IN parameters; the OUT one is registered here, e.g.
     * {@code callProcedureReturningLong("{call sp_asignar_unidad(?, ?, ?, ?, ?)}",
     * emergencyId, vehicleId, coordinatorId, null)} returns the id of the new assignment.
     * Errors are translated like in {@link #callProcedure}.
     */
    protected long callProcedureReturningLong(String call, Object... inParams) {
        return connections.withConnection(connection -> {
            try (CallableStatement statement = connection.prepareCall(call)) {
                bind(statement, inParams);
                int outIndex = (inParams == null ? 0 : inParams.length) + 1;
                statement.registerOutParameter(outIndex, Types.NUMERIC);
                statement.execute();
                long value = statement.getLong(outIndex);
                if (statement.wasNull()) {
                    throw new DataAccessException("The procedure returned NULL in its OUT parameter: " + call, null);
                }
                return value;
            } catch (SQLException e) {
                rejectIfApplicationError(e);
                throw e;
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

    /** Throws BusinessRuleException if the error came from RAISE_APPLICATION_ERROR; otherwise does nothing. */
    static void rejectIfApplicationError(SQLException error) {
        if (isApplicationError(error)) {
            throw new BusinessRuleException(applicationMessage(error));
        }
    }

    /** True for ORA-20000 … ORA-20999 (getErrorCode() gives the number without the sign). */
    static boolean isApplicationError(SQLException error) {
        int code = error.getErrorCode();
        return code >= FIRST_APPLICATION_ERROR && code <= LAST_APPLICATION_ERROR;
    }

    /**
     * The procedure's message without the Oracle prefix and without the ORA-06512 lines
     * that point to the PL/SQL line: "ORA-20004: La unidad 5 no esta…\nORA-06512: at…"
     * → "La unidad 5 no esta…".
     */
    static String applicationMessage(SQLException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String firstLine = message.lines().findFirst().orElse("").strip();
        int colon = firstLine.indexOf(": ");
        return firstLine.startsWith("ORA-") && colon > 0 ? firstLine.substring(colon + 2) : firstLine;
    }
}