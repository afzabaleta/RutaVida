package co.edu.unicesar.rutavida.dao.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Gives a JDBC connection to the DAO implementations.
 *
 * <p>Inside a transaction it returns the transaction's connection and does not close
 * it; outside a transaction it opens a new connection (auto-commit) and closes it
 * when the work ends. DAOs therefore never call {@code close()} or {@code commit()}.
 *
 * <pre>{@code
 * public Optional<Emergency> findById(long id) {
 *     return connections.withConnection(connection -> {
 *         try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
 *             ps.setLong(1, id);
 *             try (ResultSet rs = ps.executeQuery()) {
 *                 return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
 *             }
 *         }
 *     });
 * }
 * }</pre>
 */
public interface ConnectionProvider {

    /**
     * Runs {@code work} with a connection. Any SQLException is wrapped in
     * {@link co.edu.unicesar.rutavida.exception.DataAccessException}.
     */
    <T> T withConnection(SqlWork<T> work);

    /** Database work that may throw SQLException. */
    @FunctionalInterface
    interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
