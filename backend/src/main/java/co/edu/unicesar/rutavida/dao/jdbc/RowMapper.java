package co.edu.unicesar.rutavida.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts the current row of a ResultSet into an object (Oracle columns in Spanish
 * → Java attributes in English). Each JdbcXxxDAO defines one mapper per entity.
 *
 * <pre>{@code
 * RowMapper<EmergencyType> mapper = row -> new EmergencyType(
 *         row.getLong("id_tipo_emergencia"),
 *         row.getString("nombre"),
 *         row.getInt("prioridad"),
 *         row.getString("descripcion"));
 * }</pre>
 */
@FunctionalInterface
public interface RowMapper<T> {

    /** Maps the row the ResultSet is positioned on. Must not call {@code next()}. */
    T map(ResultSet row) throws SQLException;
}