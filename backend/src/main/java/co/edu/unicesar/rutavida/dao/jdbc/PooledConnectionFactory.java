package co.edu.unicesar.rutavida.dao.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decorator of {@link ConnectionFactory} that reuses physical connections.
 *
 * <p>Against Oracle Cloud every new connection pays the TLS handshake and the login across the
 * internet (about half a second), and RutaVida opens one per operation: the live tracking took
 * 8 to 13 s. Here {@link #open()} hands out an idle connection when there is one, and
 * {@code close()} on what it returns gives the connection back instead of closing it.
 *
 * <p>Before a connection goes back it is left as a new one: a transaction still open is rolled
 * back and auto-commit is turned on again. A connection idle for more than {@link #VALIDATE_AFTER}
 * is checked with {@code isValid} before it is handed out; a dead one is closed and replaced.
 * There is no upper limit on connections in use (nobody ever waits); only {@link #MAX_IDLE}
 * stay open while idle.
 *
 * <p>GoF Decorator (like {@link ClientIdentifierConnectionFactory}): JdbcTransactionManager and
 * the DAOs do not change. GoF Proxy: the connection returned is a {@link java.lang.reflect.Proxy}.
 */
public final class PooledConnectionFactory implements ConnectionFactory {

    /** Idle connections kept open; the rest are closed when given back. */
    static final int MAX_IDLE = 8;

    /** An idle connection older than this is checked before it is used again. */
    static final Duration VALIDATE_AFTER = Duration.ofSeconds(30);

    /** Seconds {@code isValid} may wait for Oracle. */
    static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private static final Logger LOG = Logger.getLogger(PooledConnectionFactory.class.getName());

    /** A physical connection waiting in the pool and since when. */
    private record Idle(Connection physical, Instant since) {
    }

    private final ConnectionFactory delegate;
    private final Clock clock;
    private final Deque<Idle> idle = new ConcurrentLinkedDeque<>();
    private final AtomicInteger idleCount = new AtomicInteger();

    public PooledConnectionFactory(ConnectionFactory delegate) {
        this(delegate, Clock.systemUTC());
    }

    /** Package-private: the tests move the clock. */
    PooledConnectionFactory(ConnectionFactory delegate, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Connection open() throws SQLException {
        Idle candidate;
        while ((candidate = idle.pollFirst()) != null) {
            idleCount.decrementAndGet();
            if (isUsable(candidate)) {
                return wrap(candidate.physical());
            }
            closeQuietly(candidate.physical());
        }
        return wrap(delegate.open());
    }

    /** Idle connections right now. Package-private for the tests. */
    int idleConnections() {
        return idleCount.get();
    }

    private boolean isUsable(Idle candidate) {
        if (Duration.between(candidate.since(), clock.instant()).compareTo(VALIDATE_AFTER) < 0) {
            return true;
        }
        try {
            return candidate.physical().isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }

    private Connection wrap(Connection physical) {
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "close" -> {
                        if (closed.compareAndSet(false, true)) {
                            giveBack(physical);
                        }
                        yield null;
                    }
                    case "isClosed" -> closed.get() || physical.isClosed();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Pooled[" + physical + "]";
                    default -> {
                        if (closed.get()) {
                            throw new SQLException("Connection already closed");
                        }
                        yield invoke(physical, method, args);
                    }
                });
    }

    private static Object invoke(Connection physical, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(physical, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Leaves the connection as new and keeps it, or closes it if it fails or the pool is full. */
    private void giveBack(Connection physical) {
        try {
            if (physical.isClosed()) {
                return;
            }
            if (!physical.getAutoCommit()) {
                physical.rollback();             // nothing half done reaches the next user
                physical.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "A connection could not be reset; it is closed", e);
            closeQuietly(physical);
            return;
        }
        if (idleCount.incrementAndGet() <= MAX_IDLE) {
            idle.offerFirst(new Idle(physical, clock.instant()));
        } else {
            idleCount.decrementAndGet();
            closeQuietly(physical);
        }
    }

    private static void closeQuietly(Connection physical) {
        try {
            physical.close();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Closing a pooled connection failed", e);
        }
    }
}
