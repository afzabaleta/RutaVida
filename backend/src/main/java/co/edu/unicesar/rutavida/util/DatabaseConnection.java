package co.edu.unicesar.rutavida.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Opens JDBC connections to the RutaVida Oracle schema.
 *
 * <p>Configuration is resolved in this order (first non-blank value wins):
 * <ol>
 *   <li>Environment variables {@code RUTAVIDA_DB_URL}, {@code RUTAVIDA_DB_USER},
 *       {@code RUTAVIDA_DB_PASSWORD}.</li>
 *   <li>The file {@code db.properties} in the working directory
 *       (keys {@code db.url}, {@code db.user}, {@code db.password}).</li>
 *   <li>Defaults for URL and user. There is no default password.</li>
 * </ol>
 *
 * <p>Each call to {@link #open()} returns a new connection. DAOs must close it
 * with try-with-resources. The password is never exposed by this class.
 */
public final class DatabaseConnection {

    public static final String DEFAULT_URL = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";
    public static final String DEFAULT_USER = "rutavida";
    public static final String CONFIG_FILE = "db.properties";

    static final String ENV_URL = "RUTAVIDA_DB_URL";
    static final String ENV_USER = "RUTAVIDA_DB_USER";
    static final String ENV_PASSWORD = "RUTAVIDA_DB_PASSWORD";

    static final String KEY_URL = "db.url";
    static final String KEY_USER = "db.user";
    static final String KEY_PASSWORD = "db.password";

    private final String url;
    private final String user;
    private final String password;

    private DatabaseConnection(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Builds the configuration from the real environment and {@code ./db.properties}. */
    public static DatabaseConnection fromEnvironment() {
        return fromSources(System.getenv(), loadProperties(Path.of(CONFIG_FILE)));
    }

    /**
     * Builds the configuration from explicit sources. Package-private so it can be
     * unit tested without touching the real environment.
     *
     * @throws IllegalStateException if no password is configured
     */
    static DatabaseConnection fromSources(Map<String, String> env, Properties file) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(file, "file");

        String url = firstNonBlank(env.get(ENV_URL), file.getProperty(KEY_URL), DEFAULT_URL);
        String user = firstNonBlank(env.get(ENV_USER), file.getProperty(KEY_USER), DEFAULT_USER);
        String password = firstNonBlank(env.get(ENV_PASSWORD), file.getProperty(KEY_PASSWORD), null);

        if (password == null) {
            throw new IllegalStateException("Database password not configured. Set "
                    + ENV_PASSWORD + " or " + KEY_PASSWORD + " in " + CONFIG_FILE);
        }
        if (!url.startsWith("jdbc:oracle:")) {
            throw new IllegalStateException("Unsupported JDBC URL (expected jdbc:oracle:...): " + url);
        }
        return new DatabaseConnection(url, user, password);
    }

    /** Opens a new connection. The caller is responsible for closing it. */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public String url() {
        return url;
    }

    public String user() {
        return user;
    }

    /** Never includes the password, so it is safe to log. */
    @Override
    public String toString() {
        return "DatabaseConnection[url=" + url + ", user=" + user + "]";
    }

    static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + path.toAbsolutePath(), e);
            }
        }
        return properties;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }
}
