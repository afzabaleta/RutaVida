package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the transaction rules with fake connections; no Oracle needed. */
class JdbcTransactionManagerTest {

    /** Records what the code does with the connection. */
    private static final class FakeConnection implements InvocationHandler {
        boolean autoCommit = true;
        boolean committed;
        boolean rolledBack;
        boolean closed;
        boolean failOnCommit;

        Connection proxy() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            switch (method.getName()) {
                case "setAutoCommit" -> autoCommit = (Boolean) args[0];
                case "getAutoCommit" -> {
                    return autoCommit;
                }
                case "commit" -> {
                    if (failOnCommit) {
                        throw new SQLException("commit failed");
                    }
                    committed = true;
                }
                case "rollback" -> rolledBack = true;
                case "close" -> closed = true;
                case "isClosed" -> {
                    return closed;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "FakeConnection";
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
            return null;
        }
    }

    private final List<FakeConnection> opened = new ArrayList<>();

    private JdbcTransactionManager manager() {
        return new JdbcTransactionManager(() -> {
            FakeConnection fake = new FakeConnection();
            opened.add(fake);
            return fake.proxy();
        });
    }

    @Test
    void commitsAndClosesWhenWorkSucceeds() {
        JdbcTransactionManager tx = manager();

        String result = tx.callInTransaction(() -> "ok");

        assertEquals("ok", result);
        FakeConnection connection = opened.get(0);
        assertFalse(connection.autoCommit);
        assertTrue(connection.committed);
        assertFalse(connection.rolledBack);
        assertTrue(connection.closed);
        assertFalse(tx.isInTransaction());
    }

    @Test
    void rollsBackWhenWorkFails() {
        JdbcTransactionManager tx = manager();

        assertThrows(IllegalStateException.class, () -> tx.runInTransaction(() -> {
            throw new IllegalStateException("business rule broken");
        }));

        FakeConnection connection = opened.get(0);
        assertTrue(connection.rolledBack);
        assertFalse(connection.committed);
        assertTrue(connection.closed);
        assertFalse(tx.isInTransaction());
    }

    @Test
    void daoCallsInsideATransactionShareOneConnection() {
        JdbcTransactionManager tx = manager();

        tx.runInTransaction(() -> {
            Connection first = tx.withConnection(c -> c);
            Connection second = tx.withConnection(c -> c);
            assertSame(first, second);
            assertFalse(opened.get(0).closed, "a DAO must not close the transaction connection");
            tx.runInTransaction(() -> tx.withConnection(c -> c));     // nested: joins
        });

        assertEquals(1, opened.size());
        assertTrue(opened.get(0).committed);
    }

    @Test
    void withConnectionOutsideTransactionOpensAndCloses() {
        JdbcTransactionManager tx = manager();

        tx.withConnection(c -> null);
        tx.withConnection(c -> null);

        assertEquals(2, opened.size());
        assertTrue(opened.get(0).closed);
        assertTrue(opened.get(1).closed);
        assertTrue(opened.get(0).autoCommit, "outside a transaction auto-commit is kept");
    }

    @Test
    void sqlExceptionIsWrappedSoJdbcDoesNotLeak() {
        JdbcTransactionManager tx = manager();

        assertThrows(DataAccessException.class, () -> tx.withConnection(c -> {
            throw new SQLException("ORA-00942: table or view does not exist");
        }));
        assertTrue(opened.get(0).closed);
    }

    @Test
    void failedCommitRollsBackAndIsWrapped() {
        JdbcTransactionManager tx = new JdbcTransactionManager(() -> {
            FakeConnection fake = new FakeConnection();
            fake.failOnCommit = true;
            opened.add(fake);
            return fake.proxy();
        });

        assertThrows(DataAccessException.class, () -> tx.runInTransaction(() -> { }));
        assertTrue(opened.get(0).rolledBack);
        assertTrue(opened.get(0).closed);
    }
}
