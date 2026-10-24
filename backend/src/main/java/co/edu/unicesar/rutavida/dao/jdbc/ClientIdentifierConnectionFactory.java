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
 * <p>Without a session ({@code currentUserId} returns null) nothing is sent: a new Oracle
 * session starts without identifier and the triggers store NULL.
 *
 * <p>The identifier is set when the connection is opened. RutaVida opens one connection per
 * operation or transaction, so a connection never outlives the session that opened it.
 *
 * <p>GoF Decorator / SOLID Open-Closed: JdbcTransactionManager does not change.
 */
public final class ClientIdentifierConnectionFactory implements ConnectionFactory {

    static final String SET_IDENTIFIER = "BEGIN DBMS_SESSION.SET_IDENTIFIER(?); END;";

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
        if (userId == null) {
            return connection;
        }
        try (CallableStatement call = connection.prepareCall(SET_IDENTIFIER)) {
            call.setString(1, Long.toString(userId));
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