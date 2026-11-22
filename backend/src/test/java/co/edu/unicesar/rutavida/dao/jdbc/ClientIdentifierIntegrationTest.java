package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CLIENT_IDENTIFIER against the local Oracle. Skipped without Oracle; nothing is written. */
class ClientIdentifierIntegrationTest {

    private static DatabaseConnection database;

    @BeforeAll
    static void connect() {
        OracleTestDatabase.connectOrSkip();
        database = DatabaseConnection.fromEnvironment();
    }

    private static String clientIdentifier(Supplier<Long> currentUserId) {
        JdbcTransactionManager jdbc = new JdbcTransactionManager(
                new ClientIdentifierConnectionFactory(database::open, currentUserId));
        return jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER') FROM dual");
                 ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getString(1);
            }
        });
    }

    @Test
    void oracleSeesTheIdOfTheLoggedInUser() {
        assertEquals("7", clientIdentifier(() -> 7L));
    }

    @Test
    void withoutASessionTheIdentifierIsEmpty() {
        assertNull(clientIdentifier(() -> null));
    }
}