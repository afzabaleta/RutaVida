package co.edu.unicesar.rutavida.dao.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Without Oracle: a fake Connection (java.lang.reflect.Proxy) records what the factory does. */
class ClientIdentifierConnectionFactoryTest {

    /** What the fake connection received. */
    private static final class Recorder {
        final List<String> calls = new ArrayList<>();
        boolean closed;
    }

    private static Connection fakeConnection(Recorder recorder, boolean failOnExecute) {
        CallableStatement statement = (CallableStatement) Proxy.newProxyInstance(
                CallableStatement.class.getClassLoader(), new Class<?>[]{CallableStatement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setString" -> recorder.calls.add("setString " + args[1]);
                        case "execute" -> {
                            if (failOnExecute) {
                                throw new SQLException("ORA-06550: fake error");
                            }
                            recorder.calls.add("execute");
                            return false;
                        }
                        case "close" -> recorder.calls.add("close statement");
                        default -> {
                            // not used by the factory
                        }
                    }
                    return null;
                });
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "prepareCall" -> {
                            recorder.calls.add("prepareCall " + args[0]);
                            return statement;
                        }
                        case "close" -> recorder.closed = true;
                        default -> {
                            // not used by the factory
                        }
                    }
                    return null;
                });
    }

    @Test
    void withASessionTheUserIdIsSentToOracle() throws SQLException {
        Recorder recorder = new Recorder();
        Connection fake = fakeConnection(recorder, false);

        Connection opened = new ClientIdentifierConnectionFactory(() -> fake, () -> 42L).open();

        assertSame(fake, opened);
        assertEquals(List.of(
                "prepareCall " + ClientIdentifierConnectionFactory.SET_IDENTIFIER,
                "setString 42",
                "execute",
                "close statement"), recorder.calls);
        assertFalse(recorder.closed);
    }

    @Test
    void withoutASessionNothingIsSent() throws SQLException {
        Recorder recorder = new Recorder();
        Connection fake = fakeConnection(recorder, false);

        Connection opened = new ClientIdentifierConnectionFactory(() -> fake, () -> null).open();

        assertSame(fake, opened);
        assertTrue(recorder.calls.isEmpty());
    }

    @Test
    void ifOracleRejectsTheIdentifierTheConnectionIsClosed() {
        Recorder recorder = new Recorder();
        Connection fake = fakeConnection(recorder, true);
        ClientIdentifierConnectionFactory factory = new ClientIdentifierConnectionFactory(() -> fake, () -> 42L);

        assertThrows(SQLException.class, factory::open);
        assertTrue(recorder.closed);
    }
}