package co.edu.unicesar.rutavida.dao.jdbc;

import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.util.DatabaseConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared setup of the DAO integration tests. A test class calls {@link #connectOrSkip()}
 * in its @BeforeAll: without db.properties or with Oracle stopped, every test of that class
 * is SKIPPED instead of failing, so a teammate without Oracle still gets a green build.
 */
final class OracleTestDatabase {

    private OracleTestDatabase() {
    }

    /** Thrown on purpose to force a rollback at the end of a test transaction. */
    static final class Rollback extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static JdbcTransactionManager connectOrSkip() {
        assumeTrue(Files.exists(Path.of(DatabaseConnection.CONFIG_FILE)),
                "No db.properties: integration tests skipped");
        DatabaseConnection database = DatabaseConnection.fromEnvironment();
        JdbcTransactionManager jdbc = new JdbcTransactionManager(database::open);
        try {
            jdbc.withConnection(connection -> connection.isValid(5));
        } catch (DataAccessException e) {
            assumeTrue(false, "Oracle not available: integration tests skipped");
        }
        return jdbc;
    }

    /** Runs {@code work} in a transaction that is always rolled back: Oracle is left unchanged. */
    static void inRolledBackTransaction(JdbcTransactionManager jdbc, Runnable work) {
        assertThrows(Rollback.class, () -> jdbc.runInTransaction(() -> {
            work.run();
            throw new Rollback();
        }));
    }

    /** Reads a single number with plain JDBC (only for test setup and checks). */
    static long number(JdbcTransactionManager jdbc, String sql, Object... params) {
        return jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return row.getLong(1);
                }
            }
        });
    }

    /** Runs an INSERT/UPDATE with plain JDBC (only for test setup). */
    static void run(JdbcTransactionManager jdbc, String sql, Object... params) {
        jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                return statement.executeUpdate();
            }
        });
    }
}