package co.edu.unicesar.rutavida.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConnectionTest {

    private static Properties file(String url, String user, String password) {
        Properties p = new Properties();
        if (url != null) p.setProperty(DatabaseConnection.KEY_URL, url);
        if (user != null) p.setProperty(DatabaseConnection.KEY_USER, user);
        if (password != null) p.setProperty(DatabaseConnection.KEY_PASSWORD, password);
        return p;
    }

    @Test
    void usesDefaultsWhenOnlyPasswordIsGiven() {
        DatabaseConnection db = DatabaseConnection.fromSources(Map.of(), file(null, null, "secret"));
        assertEquals(DatabaseConnection.DEFAULT_URL, db.url());
        assertEquals(DatabaseConnection.DEFAULT_USER, db.user());
    }

    @Test
    void environmentOverridesFile() {
        Map<String, String> env = Map.of(
                DatabaseConnection.ENV_URL, "jdbc:oracle:thin:@//otherhost:1521/XEPDB1",
                DatabaseConnection.ENV_USER, "envuser");
        DatabaseConnection db = DatabaseConnection.fromSources(env,
                file("jdbc:oracle:thin:@//localhost:1521/XEPDB1", "fileuser", "secret"));
        assertEquals("jdbc:oracle:thin:@//otherhost:1521/XEPDB1", db.url());
        assertEquals("envuser", db.user());
    }

    @Test
    void blankValuesAreIgnored() {
        DatabaseConnection db = DatabaseConnection.fromSources(
                Map.of(DatabaseConnection.ENV_USER, "   "), file(null, "fileuser", "secret"));
        assertEquals("fileuser", db.user());
    }

    @Test
    void missingPasswordFails() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseConnection.fromSources(Map.of(), file(null, null, "")));
    }

    @Test
    void nonOracleUrlFails() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseConnection.fromSources(Map.of(), file("jdbc:mysql://localhost/x", null, "secret")));
    }

    @Test
    void toStringDoesNotLeakPassword() {
        DatabaseConnection db = DatabaseConnection.fromSources(Map.of(), file(null, null, "TopSecret123"));
        assertFalse(db.toString().contains("TopSecret123"));
    }
}
