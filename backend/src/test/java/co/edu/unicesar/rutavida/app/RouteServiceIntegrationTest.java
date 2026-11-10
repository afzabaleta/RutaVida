package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.service.RouteService;
import co.edu.unicesar.rutavida.util.DatabaseConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * K6 "done" criterion on the real network: RouteService gives the recommended route and its
 * alternatives from the fire station to Parque La Popa. Nothing is written to Oracle.
 * Skipped without Oracle or without database/05_red_vial_valledupar.sql loaded.
 */
class RouteServiceIntegrationTest {

    /** Parque La Popa, Valledupar: the emergency of the demo (agreed on 21/10/2026). */
    private static final GeoPoint POPA = new GeoPoint(10.467172, -73.265099);

    private static AppContext context;

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
        assumeTrue(number("SELECT COUNT(*) FROM tramo_vial WHERE osm_way_id IS NOT NULL") > 0,
                "Run database/05_red_vial_valledupar.sql first");
    }

    private static double number(String sql) {
        return context.jdbc().withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getDouble(1);
            }
        });
    }

    @Test
    void routesFromTheFireStationToParqueLaPopa() {
        GeoPoint station = new GeoPoint(
                number("SELECT MIN(latitud) FROM estacion_emergencia WHERE nombre = 'Estacion de Bomberos'"),
                number("SELECT MIN(longitud) FROM estacion_emergencia WHERE nombre = 'Estacion de Bomberos'"));

        long start = System.nanoTime();
        RouteService service = context.routeService();
        long loadMillis = (System.nanoTime() - start) / 1_000_000;

        assertSame(service, context.routeService(), "the graph is loaded only once");
        assertTrue(service.isWithinNetwork(station));
        assertTrue(service.isWithinNetwork(POPA));

        start = System.nanoTime();
        List<RouteOption> options = service.calculateOptions(station, POPA, VehicleProfile.FIRE_TRUCK);
        long routeMillis = (System.nanoTime() - start) / 1_000_000;

        assertFalse(options.isEmpty());
        assertEquals(1, options.get(0).rank());
        assertTrue(options.get(0).distanceKm() > 0);
        double seconds = service.estimateSeconds(station, POPA, VehicleProfile.FIRE_TRUCK).orElseThrow();
        assertEquals(options.get(0).estimatedMinutes() * 60, seconds, 0.01);

        System.out.printf("RouteService: grafo cargado en %d ms; %d rutas calculadas en %d ms.%n",
                loadMillis, options.size(), routeMillis);
        for (RouteOption option : options) {
            System.out.printf("  Ruta %d: %.2f km, %.1f min. %s%n", option.rank(), option.distanceKm(),
                    option.estimatedMinutes(), option.explanation());
        }
    }
}
