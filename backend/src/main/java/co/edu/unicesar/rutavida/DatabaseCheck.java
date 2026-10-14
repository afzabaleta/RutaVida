package co.edu.unicesar.rutavida;

import co.edu.unicesar.rutavida.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Development tool: verifies that the local Oracle database is reachable and that
 * the 23 RutaVida tables exist. It is not part of the final application.
 *
 * <p>Run it from IntelliJ (working directory = backend/) after creating
 * backend/db.properties. Exit code 0 = OK, 1 = problem found.
 */
public final class DatabaseCheck {

    static final List<String> EXPECTED_TABLES = List.of(
            "ROL", "PERMISO", "ROL_PERMISO", "USUARIO", "ESTACION_EMERGENCIA",
            "TIPO_VEHICULO", "VEHICULO_EMERGENCIA", "HOSPITAL", "SERVICIO_HOSPITAL",
            "TIPO_EMERGENCIA", "EMERGENCIA", "NODO_VIAL", "TRAMO_VIAL", "SEMAFORO",
            "TRAFICO", "INCIDENTE_VIAL", "RUTA", "RUTA_TRAMO", "ASIGNACION_EMERGENCIA",
            "UBICACION_VEHICULO", "ALERTA", "NOTIFICACION", "AUDITORIA");

    private DatabaseCheck() {
    }

    public static void main(String[] args) {
        DatabaseConnection database;
        try {
            database = DatabaseConnection.fromEnvironment();
        } catch (IllegalStateException e) {
            System.err.println("[ERROR] Configuración incompleta: " + e.getMessage());
            System.err.println("        Copie db.properties.example como db.properties y escriba la contraseña.");
            System.exit(1);
            return;
        }

        System.out.println("Conectando a " + database.url() + " como " + database.user() + "...");

        try (Connection connection = database.open()) {
            DatabaseMetaData meta = connection.getMetaData();
            System.out.println("[OK] Conexión establecida: " + meta.getDatabaseProductVersion().lines().findFirst().orElse(""));

            int found = countExpectedTables(connection);
            System.out.println("Tablas de RutaVida encontradas: " + found + " de " + EXPECTED_TABLES.size());
            if (found != EXPECTED_TABLES.size()) {
                System.err.println("[ERROR] Faltan tablas. Ejecute database/01_esquema.sql con el usuario RUTAVIDA.");
                System.exit(1);
            }
            System.out.println("[OK] El esquema está completo.");
        } catch (SQLException e) {
            System.err.println("[ERROR] No se pudo conectar (" + e.getErrorCode() + "): " + e.getMessage());
            System.err.println("        Revise que Oracle XE esté iniciado y que el usuario y la contraseña sean correctos.");
            System.exit(1);
        }
    }

    private static int countExpectedTables(Connection connection) throws SQLException {
        String placeholders = String.join(",", EXPECTED_TABLES.stream().map(t -> "?").toList());
        String sql = "SELECT COUNT(*) FROM user_tables WHERE table_name IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < EXPECTED_TABLES.size(); i++) {
                statement.setString(i + 1, EXPECTED_TABLES.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
