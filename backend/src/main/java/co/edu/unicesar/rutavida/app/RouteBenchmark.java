package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.graph.AStarRouter;
import co.edu.unicesar.rutavida.graph.CostFunction;
import co.edu.unicesar.rutavida.graph.EmergencyCost;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.graph.VehicleProfile;
import co.edu.unicesar.rutavida.util.Messages;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Metrics for the presentation: A* against Dijkstra on the real road network of Valledupar.
 *
 * <p>It loads the graph from Oracle, draws {@link #DEFAULT_TRIPS} random trips with a fixed seed
 * (the same trips every time), and for each one runs both algorithms with the same cost
 * function (EmergencyCost, ambulance). Dijkstra is the same AStarRouter with
 * {@code useHeuristic = false}. For each trip it keeps the median time of
 * {@link #REPETITIONS} runs (after a warm-up, so the JIT compilation does not count), the
 * nodes each one explored and whether both found the same cost.
 *
 * <p>It prints a summary by trip length and writes every trip to a CSV file. Nothing is written
 * to Oracle. The times depend on the computer: what holds everywhere is the comparison and the
 * explored nodes. There is no traffic: the graph is loaded as it is in Oracle (traffic lights
 * and closed segments included).
 *
 * <p>Arguments (optional): number of trips and CSV file
 * (default {@code ../docs/metricas/a-estrella-vs-dijkstra.csv}). Working directory: backend/.
 */
public final class RouteBenchmark {

    public static final int DEFAULT_TRIPS = 200;
    public static final long SEED = 2026L;
    static final int WARM_UP_TRIPS = 30;
    static final int REPETITIONS = 5;
    static final double SHORT_UNTIL_METERS = 2_000;
    static final double LONG_FROM_METERS = 5_000;

    private static final String DEFAULT_CSV = "../docs/metricas/a-estrella-vs-dijkstra.csv";
    private static final VehicleProfile VEHICLE = VehicleProfile.AMBULANCE;
    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    /** One measured trip. */
    public record Trip(int from, int to, double meters, double seconds, double aStarMs, double dijkstraMs,
                       int aStarNodes, int dijkstraNodes, boolean sameCost) {
    }

    /** Averages of a group of trips. */
    public record Summary(String group, int trips, double averageKm, double aStarMs, double dijkstraMs,
                          double aStarNodes, double dijkstraNodes, int sameCost) {

        /** How many times fewer nodes A* explored. */
        public double nodeRatio() {
            return aStarNodes == 0 ? 0 : dijkstraNodes / aStarNodes;
        }

        /** How many times faster A* was. */
        public double speedUp() {
            return aStarMs == 0 ? 0 : dijkstraMs / aStarMs;
        }
    }

    private RouteBenchmark() {
    }

    public static void main(String[] args) {
        int trips = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_TRIPS;
        Path csv = Path.of(args.length > 1 ? args[1] : DEFAULT_CSV);
        System.out.println("RutaVida - A* contra Dijkstra sobre la red vial real de Valledupar");

        AppContext context;
        try {
            context = AppContext.create();
        } catch (IllegalStateException e) {
            System.err.println("[ERROR] Configuración incompleta: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            RoadGraph graph = context.graphLoader().load();
            System.out.printf(SPANISH, "[OK] Grafo: %d nodos y %d tramos. Midiendo %d viajes (semilla %d)...%n",
                    graph.nodeCount(), graph.edgeCount(), trips, SEED);
            List<Trip> measured = measure(graph, trips, new Random(SEED));
            List<Summary> summaries = summarize(measured);
            printTable(summaries, System.out);
            writeCsv(measured, csv);
            System.out.println("[OK] Cada viaje: " + csv.toAbsolutePath().normalize());
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo escribir " + csv + ": " + e.getMessage()
                    + " (la carpeta de trabajo debe ser backend/)");
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("[ERROR] " + Messages.userMessage(e));
            System.exit(1);
        }
    }

    /**
     * Measures random trips. Pairs without a route (or from a node to itself) are skipped and
     * another pair is drawn.
     */
    public static List<Trip> measure(RoadGraph graph, int trips, Random random) {
        if (trips < 1) {
            throw new IllegalArgumentException("trips must be >= 1");
        }
        AStarRouter router = new AStarRouter();
        CostFunction cost = new EmergencyCost();

        // Warm-up: the first runs include the JIT compilation and would make A* look slower.
        Random warmUp = new Random(random.nextLong());
        for (int i = 0; i < WARM_UP_TRIPS; i++) {
            int from = warmUp.nextInt(graph.nodeCount());
            int to = warmUp.nextInt(graph.nodeCount());
            router.route(graph, from, to, VEHICLE, cost, null, true);
            router.route(graph, from, to, VEHICLE, cost, null, false);
        }

        List<Trip> result = new ArrayList<>();
        int attempts = 0;
        while (result.size() < trips) {
            if (++attempts > trips * 100) {
                throw new IllegalStateException("La red vial casi no tiene rutas entre nodos al azar.");
            }
            int from = random.nextInt(graph.nodeCount());
            int to = random.nextInt(graph.nodeCount());
            if (from == to) {
                continue;
            }
            Optional<RouteResult> aStar = router.route(graph, from, to, VEHICLE, cost, null, true);
            Optional<RouteResult> dijkstra = router.route(graph, from, to, VEHICLE, cost, null, false);
            if (aStar.isEmpty() || dijkstra.isEmpty()) {
                continue;
            }
            double aStarMs = medianMillis(() -> router.route(graph, from, to, VEHICLE, cost, null, true));
            double dijkstraMs = medianMillis(() -> router.route(graph, from, to, VEHICLE, cost, null, false));
            RouteResult a = aStar.get();
            RouteResult d = dijkstra.get();
            result.add(new Trip(from, to, a.meters(), a.seconds(), aStarMs, dijkstraMs,
                    a.exploredNodes(), d.exploredNodes(), Math.abs(a.seconds() - d.seconds()) < 1e-6));
        }
        return result;
    }

    /** Median of REPETITIONS runs: one slow run (garbage collector) does not move it. */
    private static double medianMillis(Runnable work) {
        double[] times = new double[REPETITIONS];
        for (int i = 0; i < REPETITIONS; i++) {
            long start = System.nanoTime();
            work.run();
            times[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(times);
        return times[REPETITIONS / 2];
    }

    /** Short (under 2 km), medium, long (5 km or more) and all the trips. */
    public static List<Summary> summarize(List<Trip> trips) {
        List<Summary> summaries = new ArrayList<>();
        summaries.add(summary("Cortos (< 2 km)", trips, trip -> trip.meters() < SHORT_UNTIL_METERS));
        summaries.add(summary("Medios (2 a 5 km)", trips,
                trip -> trip.meters() >= SHORT_UNTIL_METERS && trip.meters() < LONG_FROM_METERS));
        summaries.add(summary("Largos (≥ 5 km)", trips, trip -> trip.meters() >= LONG_FROM_METERS));
        summaries.add(summary("Todos", trips, trip -> true));
        return summaries;
    }

    private static Summary summary(String group, List<Trip> trips, Predicate<Trip> filter) {
        List<Trip> chosen = trips.stream().filter(filter).toList();
        int n = chosen.size();
        if (n == 0) {
            return new Summary(group, 0, 0, 0, 0, 0, 0, 0);
        }
        return new Summary(group, n,
                chosen.stream().mapToDouble(Trip::meters).average().orElse(0) / 1000,
                chosen.stream().mapToDouble(Trip::aStarMs).average().orElse(0),
                chosen.stream().mapToDouble(Trip::dijkstraMs).average().orElse(0),
                chosen.stream().mapToInt(Trip::aStarNodes).average().orElse(0),
                chosen.stream().mapToInt(Trip::dijkstraNodes).average().orElse(0),
                (int) chosen.stream().filter(Trip::sameCost).count());
    }

    static void printTable(List<Summary> summaries, PrintStream out) {
        out.println();
        out.printf(SPANISH, "%-18s %6s %8s %12s %12s %10s %10s %10s %8s %12s%n",
                "Grupo", "Viajes", "Km prom.", "A* (ms)", "Dijkstra(ms)", "Nodos A*", "Nodos Dij.",
                "Menos nod.", "Más ráp.", "Mismo costo");
        for (Summary summary : summaries) {
            out.printf(SPANISH, "%-18s %6d %8.2f %12.3f %12.3f %10.0f %10.0f %9.1fx %7.1fx %8d de %d%n",
                    summary.group(), summary.trips(), summary.averageKm(), summary.aStarMs(), summary.dijkstraMs(),
                    summary.aStarNodes(), summary.dijkstraNodes(), summary.nodeRatio(), summary.speedUp(),
                    summary.sameCost(), summary.trips());
        }
        out.println();
        out.println("Tiempos: mediana de " + REPETITIONS + " ejecuciones por viaje, después de calentar la JVM."
                + " Dependen del computador; los nodos explorados no.");
    }

    /** One line per trip, separated by ';' and with decimal point, so Excel and Python read it. */
    static void writeCsv(List<Trip> trips, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>();
        lines.add("# RutaVida - A* contra Dijkstra, red vial OSM de Valledupar, semilla " + SEED + ", "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        lines.add("viaje;nodo_origen;nodo_destino;metros;segundos;astar_ms;dijkstra_ms;"
                + "astar_nodos;dijkstra_nodos;mismo_costo");
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            lines.add(String.format(Locale.ROOT, "%d;%d;%d;%.1f;%.1f;%.4f;%.4f;%d;%d;%s",
                    i + 1, trip.from(), trip.to(), trip.meters(), trip.seconds(), trip.aStarMs(), trip.dijkstraMs(),
                    trip.aStarNodes(), trip.dijkstraNodes(), trip.sameCost() ? "SI" : "NO"));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
