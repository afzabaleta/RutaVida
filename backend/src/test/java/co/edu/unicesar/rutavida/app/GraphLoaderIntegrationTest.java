package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.graph.AStarRouter;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * K5 "done" criterion: the graph is built from the network loaded in Oracle (K4) and the
 * route engine finds a route from the fire station of 03_datos_valledupar.sql to Parque
 * La Popa (the place of the demo emergency). Nothing is written. Skipped without Oracle
 * or without database/05_red_vial_valledupar.sql loaded.
 */
class GraphLoaderIntegrationTest {

    /** Parque La Popa, Valledupar: the emergency of the demo (agreed on 21/10/2026). */
    private static final double POPA_LAT = 10.467172;
    private static final double POPA_LON = -73.265099;

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
    void theRealNetworkLoadsAndARouteReachesParqueLaPopa() {
        long start = System.nanoTime();
        RoadGraph graph = context.graphLoader().load();
        long loadMillis = (System.nanoTime() - start) / 1_000_000;

        double activeNodes = number("SELECT COUNT(*) FROM nodo_vial WHERE estado = 'A'");
        assertTrue(graph.nodeCount() > 0 && graph.nodeCount() <= activeNodes);
        assertTrue(graph.edgeCount() > graph.nodeCount(), "a street network has more segments than crossings");

        double stationLat = number("SELECT MIN(latitud) FROM estacion_emergencia WHERE nombre = 'Estacion de Bomberos'");
        double stationLon = number("SELECT MIN(longitud) FROM estacion_emergencia WHERE nombre = 'Estacion de Bomberos'");
        int station = graph.nearestNode(stationLat, stationLon);
        int popa = graph.nearestNode(POPA_LAT, POPA_LON);
        AStarRouter router = new AStarRouter();

        RouteResult aStar = router.route(graph, station, popa, VehicleProfile.FIRE_TRUCK,
                        new EmergencyCost(), null, true)
                .orElseThrow(() -> new AssertionError("No route from the fire station to Parque La Popa"));
        RouteResult dijkstra = router.route(graph, station, popa, VehicleProfile.FIRE_TRUCK,
                new EmergencyCost(), null, false).orElseThrow();

        assertTrue(aStar.meters() > 0);
        assertEquals(dijkstra.seconds(), aStar.seconds(), 1e-6, "A* and Dijkstra give the same cost");
        assertTrue(aStar.exploredNodes() <= dijkstra.exploredNodes(), "A* explores fewer nodes");
        System.out.printf("Grafo real: %d nodos, %d tramos, cargado en %d ms. Estación -> Parque La Popa:"
                        + " %.0f m, %.0f s; A* exploró %d nodos y Dijkstra %d.%n",
                graph.nodeCount(), graph.edgeCount(), loadMillis, aStar.meters(), aStar.seconds(),
                aStar.exploredNodes(), dijkstra.exploredNodes());
    }
}