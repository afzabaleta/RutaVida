package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.graph.AStarRouter;
import co.edu.unicesar.rutavida.graph.AlternativeRouteFinder;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.service.RouteExplanationBuilder;
import co.edu.unicesar.rutavida.util.Messages;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Demo tool for the progress presentation (it is not the final dispatch flow): loads the
 * real road network from Oracle, calculates the recommended route and two alternatives
 * from the fire station to an EXAMPLE emergency at Parque La Popa, compares A* with
 * Dijkstra, applies a SIMULATED traffic jam ahead of the unit, recalculates, and calculates
 * the transfer to the Hospital Rosario Pumarejo. It writes everything as GeoJSON for the map.
 *
 * <p>Nothing is written to Oracle. Times use the speeds assumed by the OSM importer (K4)
 * and the vehicle profiles; they are estimates, not measurements.
 *
 * <p>Argument: output folder (default {@code ../frontend/data}). Run it with backend/ as
 * working directory. Exit code 0 = OK, 1 = problem found.
 */
public final class RouteDemo {

    /** Parque La Popa, Valledupar: place of the EXAMPLE emergency of the demo. */
    static final GeoPoint EMERGENCY = new GeoPoint(10.467172, -73.265099);
    static final String STATION = "Estacion de Bomberos";
    static final String HOSPITAL = "HOSPITAL ROSARIO PUMAREJO DE LOPEZ";

    /** Simulated congestion of the jam: 4.0 = four times slower for normal traffic. */
    static final double JAM = 4.0;
    static final VehicleProfile VEHICLE = VehicleProfile.AMBULANCE;

    private static final String DEFAULT_FOLDER = "../frontend/data";
    private static final int TIMING_RUNS = 20;
    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private static final String ROUTE_SOURCE =
            "Calculada por RutaVida (A*) sobre la red OSM de 05_red_vial_valledupar.sql; velocidades asumidas";
    private static final String JAM_SOURCE = "SIMULACION: congestión forzada para la demostración";

    private RouteDemo() {
    }

    public static void main(String[] args) {
        Path folder = Path.of(args.length > 0 ? args[0] : DEFAULT_FOLDER);
        System.out.println("RutaVida - Demostración del motor de rutas");
        System.out.println("Red vial real (OSM) cargada desde Oracle. Emergencia de EJEMPLO."
                + " La congestión es SIMULACION.");

        AppContext context;
        try {
            context = AppContext.create();
        } catch (IllegalStateException e) {
            System.err.println("[ERROR] Configuración incompleta: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            long start = System.nanoTime();
            RoadGraph graph = context.graphLoader().load();
            System.out.printf(SPANISH, "%n[OK] Grafo cargado: %d nodos y %d tramos en %d ms%n",
                    graph.nodeCount(), graph.edgeCount(), (System.nanoTime() - start) / 1_000_000);

            GeoPoint station = place(context,
                    "SELECT latitud, longitud FROM estacion_emergencia WHERE nombre = ?", STATION);
            GeoPoint hospital = place(context,
                    "SELECT latitud, longitud FROM hospital WHERE nombre = ?", HOSPITAL);

            RouteGeoJsonWriter map = simulate(graph, station, hospital, context.clock(), System.out);

            Files.createDirectories(folder);
            Path geoJson = folder.resolve("rutas-demo.geojson");
            Path script = folder.resolve("rutas-demo.js");
            map.writeGeoJson(geoJson);
            map.writeScript(script, "RUTAVIDA_RUTAS_DEMO");
            System.out.println();
            System.out.println("[OK] Mapa: " + geoJson.toAbsolutePath().normalize()
                    + " (" + map.featureCount() + " elementos)");
            System.out.println("[OK] Mismo contenido para abrir sin servidor: " + script.toAbsolutePath().normalize());
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo escribir el archivo: " + e.getMessage()
                    + " (la carpeta de trabajo debe ser backend/)");
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("[ERROR] " + Messages.userMessage(e));
            System.exit(1);
        }
    }

    /** Coordinates of a station or hospital loaded by database/03_datos_valledupar.sql. */
    private static GeoPoint place(AppContext context, String sql, String name) {
        return context.jdbc().withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        throw new IllegalStateException("No está '" + name
                                + "' en Oracle: ejecute database/03_datos_valledupar.sql");
                    }
                    return new GeoPoint(row.getDouble(1), row.getDouble(2));
                }
            }
        });
    }

    /**
     * The whole demo on a graph already loaded (no Oracle here, so it can be tested).
     * The graph is modified: the jam stays applied when this method returns.
     */
    static RouteGeoJsonWriter simulate(RoadGraph graph, GeoPoint station, GeoPoint hospital,
                                       Clock clock, PrintStream out) {
        AStarRouter router = new AStarRouter();
        EmergencyCost cost = new EmergencyCost();
        AlternativeRouteFinder finder = new AlternativeRouteFinder(router, cost, 0.8, 1.5);
        RouteExplanationBuilder explainer = new RouteExplanationBuilder();
        RouteGeoJsonWriter map = new RouteGeoJsonWriter("RutaVida - demostración del motor de rutas",
                "Red vial real de OpenStreetMap; emergencia de EJEMPLO; congestión SIMULACION;"
                        + " tiempos estimados con velocidades asumidas, no medidos.");

        int stationNode = graph.nearestNode(station.latitude(), station.longitude());
        int sceneNode = graph.nearestNode(EMERGENCY.latitude(), EMERGENCY.longitude());
        int hospitalNode = graph.nearestNode(hospital.latitude(), hospital.longitude());
        map.addPlace("estacion", STATION, station, "Oracle (03_datos_valledupar.sql)")
                .addPlace("emergencia", "Emergencia de EJEMPLO - Parque La Popa", EMERGENCY,
                        "EJEMPLO: emergencia de demostración")
                .addPlace("hospital", HOSPITAL, hospital, "Oracle (03_datos_valledupar.sql)");

        // 1. Station -> emergency, without simulated traffic.
        out.println();
        out.println("1) Estación -> emergencia en el Parque La Popa (" + VEHICLE.type() + ", sin tráfico simulado)");
        List<RouteResult> initial = routes(finder, graph, stationNode, sceneNode, "la estación", "la emergencia");
        List<RouteOption> initialOptions = print(initial, explainer, graph, out);
        add(map, "ida", "inicial", initialOptions, ROUTE_SOURCE);

        // 2. A* against Dijkstra on the same trip.
        compareAlgorithms(router, cost, graph, stationNode, sceneNode, out);

        // 3. SIMULATED jam ahead of the unit, then recalculation from where the unit is.
        RouteResult best = initial.get(0);
        List<Integer> edges = best.edgeIds();
        int n = edges.size();
        int travelled = n / 4;
        int jamFrom = Math.max(travelled, n / 3);
        int jamTo = Math.min(n, Math.max(jamFrom + 1, 2 * n / 3));
        List<Integer> jammed = edges.subList(jamFrom, jamTo);
        int unitNode = graph.edge(edges.get(travelled)).from();

        long now = clock.millis();
        for (int edge : jammed) {
            EdgeState state = graph.state(edge);
            graph.update(edge, new EdgeState(Math.max(state.congestion(), JAM), state.closed(),
                    state.signalDelaySec(), state.incidentFactor(), now));
        }
        Node unit = graph.node(unitNode);
        map.addPlace("unidad", "Unidad en camino (posición SIMULADA)", new GeoPoint(unit.lat(), unit.lon()),
                "SIMULACION: posición simulada, no es GPS");
        map.addCongestion("Congestión SIMULADA", path(graph, jammed), JAM, JAM_SOURCE);

        RouteResult previous = sameRoute(graph, cost, edges.subList(travelled, n));
        out.println();
        out.printf(SPANISH, "3) SIMULACION: la unidad lleva %d de %d tramos y se congestionan %d tramos adelante"
                + " (congestión %.1f)%n", travelled, n, jammed.size(), JAM);
        List<RouteResult> recalculated = routes(finder, graph, unitNode, sceneNode, "la unidad", "la emergencia");
        RouteResult newBest = recalculated.get(0);
        out.printf(SPANISH, "   Seguir por la ruta anterior: %.1f min. Nueva ruta recomendada: %.1f min.%n",
                previous.seconds() / 60, newBest.seconds() / 60);
        out.println(newBest.edgeIds().equals(previous.edgeIds())
                ? "   El motor mantiene la ruta: sigue siendo la más rápida."
                : String.format(SPANISH, "   El motor cambia de ruta y ahorra %.1f min.",
                (previous.seconds() - newBest.seconds()) / 60));
        add(map, "ida", "congestion-simulada", print(recalculated, explainer, graph, out),
                ROUTE_SOURCE + "; congestión SIMULACION");

        // 4. Transfer from the emergency to the hospital, with the jam still there.
        out.println();
        out.println("4) Emergencia -> " + HOSPITAL + " (traslado, con la congestión simulada)");
        List<RouteResult> transfer = routes(finder, graph, sceneNode, hospitalNode, "la emergencia", "el hospital");
        add(map, "traslado", "congestion-simulada", print(transfer, explainer, graph, out),
                ROUTE_SOURCE + "; congestión SIMULACION");
        return map;
    }

    private static List<RouteResult> routes(AlternativeRouteFinder finder, RoadGraph graph, int from, int to,
                                            String fromName, String toName) {
        List<RouteResult> found = finder.find(graph, from, to, VEHICLE, 3);
        if (found.isEmpty()) {
            throw new IllegalStateException("No hay ruta desde " + fromName + " hasta " + toName);
        }
        if (found.get(0).edgeIds().isEmpty()) {
            throw new IllegalStateException(fromName + " y " + toName + " quedan en el mismo nodo de la red");
        }
        return found;
    }

    private static List<RouteOption> print(List<RouteResult> routes, RouteExplanationBuilder explainer,
                                           RoadGraph graph, PrintStream out) {
        List<RouteOption> options = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            RouteOption option = explainer.toOption(i + 1, routes.get(i), routes.get(0), graph);
            options.add(option);
            out.printf(SPANISH, "   Ruta %d: %.2f km, %.1f min, %d tramos. %s%n", option.rank(),
                    option.distanceKm(), option.estimatedMinutes(), option.segmentIds().size(),
                    option.explanation());
        }
        if (routes.size() < 3) {
            out.println("   (Solo se encontraron " + routes.size()
                    + " rutas distintas que cumplen los límites de solapamiento y demora)");
        }
        return options;
    }

    private static void add(RouteGeoJsonWriter map, String leg, String scenario, List<RouteOption> options,
                            String source) {
        for (RouteOption option : options) {
            map.addRoute(leg, scenario, option, source);
        }
    }

    private static void compareAlgorithms(AStarRouter router, EmergencyCost cost, RoadGraph graph,
                                          int from, int to, PrintStream out) {
        RouteResult aStar = router.route(graph, from, to, VEHICLE, cost, null, true).orElseThrow();
        RouteResult dijkstra = router.route(graph, from, to, VEHICLE, cost, null, false).orElseThrow();
        double aStarMs = averageMillis(router, cost, graph, from, to, true);
        double dijkstraMs = averageMillis(router, cost, graph, from, to, false);
        out.println();
        out.printf(SPANISH, "2) A* exploró %d nodos (%.2f ms); Dijkstra exploró %d (%.2f ms)."
                        + " Mismo tiempo de viaje: %s.%n",
                aStar.exploredNodes(), aStarMs, dijkstra.exploredNodes(), dijkstraMs,
                Math.abs(aStar.seconds() - dijkstra.seconds()) < 1e-6 ? "sí" : "NO");
    }

    /** Average of TIMING_RUNS calculations after a warm-up (the first runs include JIT compilation). */
    private static double averageMillis(AStarRouter router, EmergencyCost cost, RoadGraph graph,
                                        int from, int to, boolean useHeuristic) {
        for (int i = 0; i < 5; i++) {
            router.route(graph, from, to, VEHICLE, cost, null, useHeuristic);
        }
        long start = System.nanoTime();
        for (int i = 0; i < TIMING_RUNS; i++) {
            router.route(graph, from, to, VEHICLE, cost, null, useHeuristic);
        }
        return (System.nanoTime() - start) / 1e6 / TIMING_RUNS;
    }

    /** The rest of the route the unit was following, with the current state of its segments. */
    private static RouteResult sameRoute(RoadGraph graph, EmergencyCost cost, List<Integer> edges) {
        double seconds = 0;
        double meters = 0;
        for (int edge : edges) {
            seconds += cost.seconds(graph.edge(edge), graph.state(edge), VEHICLE);
            meters += graph.edge(edge).lengthM();
        }
        return new RouteResult(edges, seconds, meters, 0);
    }

    private static List<GeoPoint> path(RoadGraph graph, List<Integer> edges) {
        List<GeoPoint> points = new ArrayList<>();
        for (int index : edges) {
            Edge edge = graph.edge(index);
            if (points.isEmpty()) {
                points.add(new GeoPoint(graph.node(edge.from()).lat(), graph.node(edge.from()).lon()));
            }
            points.add(new GeoPoint(graph.node(edge.to()).lat(), graph.node(edge.to()).lon()));
        }
        return points;
    }
}