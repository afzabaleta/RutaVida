package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A3 "done" criterion, end to end against the local Oracle: an administrator created by
 * AdminBootstrapService logs in through LoginController (wired by AppContext) and AUDITORIA
 * gets LOGIN / EXITO. Everything runs in a transaction that is rolled back.
 * Skipped without db.properties or with Oracle stopped.
 */
class LoginIntegrationTest {

    private static final String USERNAME = "a3_junit_admin";

    private static AppContext context;

    /** Thrown on purpose to roll the test transaction back. */
    private static final class Rollback extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @BeforeAll
    static void connect() {
        assumeTrue(Files.exists(Path.of(DatabaseConnection.CONFIG_FILE)),
                "No db.properties: integration tests skipped");
        context = AppContext.create();
        try (Connection connection = context.database().open()) {
            assumeTrue(connection.isValid(5), "Oracle not available: integration tests skipped");
        } catch (SQLException e) {
            assumeTrue(false, "Oracle not available: integration tests skipped");
        }
    }

    private static long auditRows(long userId, String operation) {
        return context.jdbc().withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM auditoria WHERE id_usuario = ? AND modulo = 'SEGURIDAD'"
                            + " AND operacion = ? AND resultado = 'EXITO'")) {
                statement.setLong(1, userId);
                statement.setString(2, operation);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return row.getLong(1);
                }
            }
        });
    }

    @Test
    void aNewAdministratorLogsInAndTheLoginIsAudited() {
        assertThrows(Rollback.class, () -> context.transactions().runInTransaction(() -> {
            User admin = context.adminBootstrap().createAdmin("JUNITA3001", "Prueba", "A3",
                    "a3.junit@ejemplo.com", USERNAME, "Clave-Segura1".toCharArray());

            OperationResult<UserSession> login =
                    context.loginController().login(USERNAME, "Clave-Segura1".toCharArray());

            assertTrue(login.success(), login.message());
            assertTrue(context.session().isActive());
            assertEquals(admin.getId(), context.session().current().user().getId());
            assertEquals("ADMINISTRADOR", context.session().current().role().getName());
            assertEquals(1, auditRows(admin.getId(), "CREAR_ADMIN"));
            assertEquals(1, auditRows(admin.getId(), "LOGIN"));

            context.loginController().logout();
            assertFalse(context.session().isActive());
            assertEquals(1, auditRows(admin.getId(), "LOGOUT"));

            throw new Rollback();
        }));

        // Checked with SQL, not with a login: a failed login would write a FALLO audit row.
        assertEquals(0, usersNamed(USERNAME), "the rolled-back user must not exist");
    }

    private static long usersNamed(String username) {
        return context.jdbc().withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM usuario WHERE username = ?")) {
                statement.setString(1, username);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return row.getLong(1);
                }
            }
        });
    }
}