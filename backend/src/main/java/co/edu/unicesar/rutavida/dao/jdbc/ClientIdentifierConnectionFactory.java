package co.edu.unicesar.rutavida.dao.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Decorator of {@link ConnectionFactory}: every new connection carries the id of the
 * logged-in user in Oracle's CLIENT_IDENTIFIER. The audit triggers of 04_plsql.sql read it
 * with {@code fn_usuario_sesion} to know who changed a status.
 *
 * <p>Without a session ({@code currentUserId} returns null, for example the simulation) the
 * identifier is cleared, so the triggers store NULL.
 *
 * <p>The identifier is set every time a connection is handed out. The connections come from
 * {@link PooledConnectionFactory} and are reused, so the one a simulation step receives may
 * still carry the user of the request that used it before: clearing it is what keeps the audit
 * from blaming that user.
 *
 * <p>GoF Decorator / SOLID Open-Closed: JdbcTransactionManager does not change.
 */
public final class ClientIdentifierConnectionFactory implements ConnectionFactory {

    static final String SET_IDENTIFIER = "BEGIN DBMS_SESSION.SET_IDENTIFIER(?); END;";
    static final String CLEAR_IDENTIFIER = "BEGIN DBMS_SESSION.CLEAR_IDENTIFIER; END;";

    private final ConnectionFactory delegate;
    private final Supplier<Long> currentUserId;

    public ClientIdentifierConnectionFactory(ConnectionFactory delegate, Supplier<Long> currentUserId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");
    }

    @Override
    public Connection open() throws SQLException {
        Connection connection = delegate.open();
        Long userId = currentUserId.get();
        try (CallableStatement call = connection.prepareCall(userId == null ? CLEAR_IDENTIFIER : SET_IDENTIFIER)) {
            if (userId != null) {
                call.setString(1, Long.toString(userId));
            }
            call.execute();
            return connection;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException closing) {
                e.addSuppressed(closing);
            }
            throw e;
        }
    }
}