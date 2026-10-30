package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.dao.TransactionManager;
import co.edu.unicesar.rutavida.exception.DataAccessException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC implementation of {@link TransactionManager}. It also gives the DAOs their connection
 * ({@link #withConnection}): inside a transaction, the transaction's connection; outside, a new one.
 *
 * <p>The open transaction is kept in a {@link ThreadLocal}: every DAO called from the
 * same thread inside {@code callInTransaction} uses the same connection, so the
 * service never has to pass a {@code Connection} around.
 *
 * <p>GRASP Pure Fabrication: this class does not represent any domain concept; it
 * exists to keep transaction handling out of services and DAOs.
 */
public final class JdbcTransactionManager implements TransactionManager {

    /** Database work that may throw SQLException. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private static final Logger LOG = Logger.getLogger(JdbcTransactionManager.class.getName());

    private final ConnectionFactory factory;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public JdbcTransactionManager(ConnectionFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public <T> T callInTransaction(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (current.get() != null) {
            return work.get();                       // join the transaction already open
        }

        Connection connection = openForTransaction();
        current.set(connection);
        try {
            T result = work.get();
            connection.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly(connection, e);
            throw new DataAccessException("Commit failed", e);
        } catch (RuntimeException | Error e) {
            rollbackQuietly(connection, e);
            throw e;
        } finally {
            current.remove();
            closeQuietly(connection);
        }
    }

    /** Runs {@code work} with a connection; DAOs never call close() or commit(). */
    public <T> T withConnection(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        Connection transactional = current.get();
        try {
            if (transactional != null) {
                return work.apply(transactional);    // the transaction owner closes it
            }
            try (Connection connection = factory.open()) {
                return work.apply(connection);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database operation failed: " + e.getMessage(), e);
        }
    }

    /** True if the current thread is inside {@link #callInTransaction}. */
    public boolean isInTransaction() {
        return current.get() != null;
    }

    private Connection openForTransaction() {
        try {
            Connection connection = factory.open();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open a transaction: " + e.getMessage(), e);
        }
    }

    private static void rollbackQuietly(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
            LOG.log(Level.WARNING, "Rollback failed", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Closing the connection failed", e);
        }
    }
}
