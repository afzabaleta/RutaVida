package co.edu.unicesar.rutavida.dao.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Without Oracle: fake physical connections (java.lang.reflect.Proxy) record what the pool does. */
class PooledConnectionFactoryTest {

    /** A fake physical connection and what it received. */
    private static final class Fake {
        final int number;
        final List<String> calls = new ArrayList<>();
        boolean autoCommit = true;
        boolean closed;
        boolean valid = true;
        final Connection connection;

        Fake(int number) {
            this.number = number;
            this.connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> {
                            autoCommit = (Boolean) args[0];
                            calls.add("setAutoCommit " + autoCommit);
                            yield null;
                        }
                        case "rollback", "commit" -> {
                            calls.add(method.getName());
                            yield null;
                        }
                        case "isValid" -> {
                            calls.add("isValid");
                            yield valid;
                        }
                        case "isClosed" -> closed;
                        case "close" -> {
                            closed = true;
                            yield null;
                        }
                        case "getSchema" -> "FAKE" + number;
                        case "hashCode" -> number;
                        case "equals" -> proxy == args[0];
                        case "toString" -> "fake" + number;
                        default -> null;
                    });
        }
    }

    /** Makes a new fake every time the pool asks for a physical connection. */
    private static final class Opener implements ConnectionFactory {
        final List<Fake> opened = new ArrayList<>();

        @Override
        public Connection open() {
            Fake fake = new Fake(opened.size() + 1);
            opened.add(fake);
            return fake.connection;
        }
    }

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-22T05:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void aClosedConnectionIsReusedInsteadOfOpeningAnother() throws SQLException {
        Opener opener = new Opener();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener);

        Connection first = pool.open();
        assertEquals("FAKE1", first.getSchema());
        first.close();
        Connection second = pool.open();

        assertEquals("FAKE1", second.getSchema());
        assertEquals(1, opener.opened.size());
        assertFalse(opener.opened.get(0).closed, "the physical connection stays open");
    }

    @Test
    void twoConnectionsInUseAreDifferentPhysicalConnections() throws SQLException {
        Opener opener = new Opener();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener);

        Connection first = pool.open();
        Connection second = pool.open();

        assertFalse(first == second, "two different connections");
        assertEquals("FAKE1", first.getSchema());
        assertEquals("FAKE2", second.getSchema());
        assertEquals(2, opener.opened.size());
    }

    @Test
    void anOpenTransactionIsRolledBackAndAutoCommitTurnedOnBeforeReuse() throws SQLException {
        Opener opener = new Opener();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener);

        Connection connection = pool.open();
        connection.setAutoCommit(false);
        connection.close();

        Fake fake = opener.opened.get(0);
        assertEquals(List.of("setAutoCommit false", "rollback", "setAutoCommit true"), fake.calls);
        assertTrue(pool.open().getAutoCommit());
    }

    @Test
    void aConnectionWithAutoCommitIsGivenBackWithoutExtraCalls() throws SQLException {
        Opener opener = new Opener();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener);

        pool.open().close();

        assertTrue(opener.opened.get(0).calls.isEmpty());
        assertEquals(1, pool.idleConnections());
    }

    @Test
    void closingTwiceGivesTheConnectionBackOnlyOnce() throws SQLException {
        Opener opener = new Opener();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener);

        Connection connection = pool.open();
        connection.close();
        connection.close();

        assertEquals(1, pool.idleConnections());
        assertTrue(connection.isClosed());
    }

    @Test
    void aClosedConnectionCannotBeUsedAnymore() throws SQLException {
        PooledConnectionFactory pool = new PooledConnectionFactory(new Opener());

        Connection connection = pool.open();
        connection.close();

        assertThrows(SQLException.class, connection::getSchema);
    }

    @Test
    void aConnectionIdleForLongIsCheckedAndADeadOneIsReplaced() throws SQLException {
        Opener opener = new Opener();
        MovableClock clock = new MovableClock();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener, clock);

        pool.open().close();
        opener.opened.get(0).valid = false;              // Oracle dropped it while idle
        clock.now = clock.now.plus(PooledConnectionFactory.VALIDATE_AFTER).plus(Duration.ofSeconds(1));

        Connection connection = pool.open();

        assertEquals("FAKE2", connection.getSchema());
        assertTrue(opener.opened.get(0).calls.contains("isValid"));
        assertTrue(opener.opened.get(0).closed);
    }

    @Test
    void aRecentlyUsedConnectionIsNotChecked() throws SQLException {
        Opener opener = new Opener();
        MovableClock clock = new MovableClock();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener, clock);

        pool.open().close();
        clock.now = clock.now.plusSeconds(5);
        pool.open();

        assertFalse(opener.opened.get(0).calls.contains("isValid"));
    }

    @Test
    void onlyMaxIdleConnectionsStayOpen() throws SQLException {
        Opener opener = new Opener();
        PooledConnectionFactory pool = new PooledConnectionFactory(opener);
        List<Connection> inUse = new ArrayList<>();
        for (int i = 0; i < PooledConnectionFactory.MAX_IDLE + 2; i++) {
            inUse.add(pool.open());
        }

        for (Connection connection : inUse) {
            connection.close();
        }

        assertEquals(PooledConnectionFactory.MAX_IDLE, pool.idleConnections());
        assertEquals(2, opener.opened.stream().filter(fake -> fake.closed).count());
    }

    @Test
    void aConnectionThatCannotBeResetIsClosedInsteadOfReused() throws SQLException {
        Fake broken = new Fake(1);
        Connection failing = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> false;
                    case "rollback" -> throw new SQLException("ORA-03113: fake end-of-file on channel");
                    case "close" -> {
                        broken.closed = true;
                        yield null;
                    }
                    case "isClosed" -> broken.closed;
                    default -> null;
                });
        PooledConnectionFactory pool = new PooledConnectionFactory(() -> failing);

        pool.open().close();

        assertTrue(broken.closed);
        assertEquals(0, pool.idleConnections());
    }
}
