package co.edu.unicesar.rutavida.service;

import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.EdgeState;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;
import co.edu.unicesar.rutavida.graph.RouteResult;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RouteType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns a RouteResult of the routing engine into what the coordinator sees: the numbers of the
 * route and a sentence in Spanish saying why it is recommended or how it compares with the
 * recommended one (CU-08, step 5). It reads the graph; it never modifies it.
 */
public class RouteExplanationBuilder {

    /** Congestion from which a segment counts as congested (1.5 = 50 % slower). Assumption to calibrate. */
    public static final double CONGESTED_FROM = 1.5;

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final double SAME_MINUTES = 0.05;
    private static final double SAME_KM = 0.05;

    /** Counts of one route, taken from the current state of its segments. */
    private record Facts(double minutes, double km, int lights, int congested, int incidents) {
    }

    /** Builds the option for the map and the panel. {@code best} is the recommended route (rank 1). */
    public RouteOption toOption(int rank, RouteResult route, RouteResult best, RoadGraph graph) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(best, "best");
        Objects.requireNonNull(graph, "graph");
        Facts facts = facts(route, graph);
        return new RouteOption(rank, rank == 1 ? RouteType.MAIN : RouteType.ALTERNATIVE,
                path(route, graph), segmentIds(route, graph), facts.km(), facts.minutes(),
                facts.lights(), facts.congested(), explain(route, best, graph));
    }

    /** Why {@code option} is recommended, or how it compares with {@code best}. */
    public String explain(RouteResult option, RouteResult best, RoadGraph graph) {
        Objects.requireNonNull(option, "option");
        Objects.requireNonNull(best, "best");
        Objects.requireNonNull(graph, "graph");
        if (option.edgeIds().isEmpty()) {
            return "El origen y el destino están en el mismo punto.";
        }
        Facts mine = facts(option, graph);
        StringBuilder text = new StringBuilder();

        if (option.edgeIds().equals(best.edgeIds())) {
            text.append("Ruta recomendada: es la más rápida, ").append(minutes(mine.minutes()))
                    .append(" y ").append(km(mine.km())).append(". Tiene ")
                    .append(count(mine.lights(), "semáforo", "semáforos")).append(" y ")
                    .append(count(mine.congested(), "tramo congestionado", "tramos congestionados"))
                    .append('.');
            if (mine.incidents() > 0) {
                text.append(" Pasa por ")
                        .append(count(mine.incidents(), "tramo con incidente", "tramos con incidente"))
                        .append('.');
            }
            return text.toString();
        }

        Facts other = facts(best, graph);
        double diffMinutes = mine.minutes() - other.minutes();
        String summary = " (" + minutes(mine.minutes()) + ", " + km(mine.km()) + ").";
        if (diffMinutes >= SAME_MINUTES) {
            text.append("Alternativa: ").append(minutes(diffMinutes))
                    .append(" más lenta que la recomendada").append(summary);
        } else if (diffMinutes <= -SAME_MINUTES) {
            text.append("Alternativa: ").append(minutes(-diffMinutes))
                    .append(" más rápida que la recomendada").append(summary);
        } else {
            text.append("Alternativa: tarda lo mismo que la recomendada").append(summary);
        }

        double diffKm = other.km() - mine.km();
        if (diffKm >= SAME_KM) {
            text.append(" Es ").append(km(diffKm)).append(" más corta.");
        } else if (diffKm <= -SAME_KM) {
            text.append(" Es ").append(km(-diffKm)).append(" más larga.");
        }
        compare(text, other.congested() - mine.congested(), "tramo congestionado",
                "tramos congestionados", "Evita %s.", "Tiene %s más.");
        compare(text, other.lights() - mine.lights(), "semáforo", "semáforos",
                "Tiene %s menos.", "Tiene %s más.");
        compare(text, other.incidents() - mine.incidents(), "tramo con incidente",
                "tramos con incidente", "Evita %s.", "Pasa por %s más.");
        return text.toString();
    }

    /** Adds one comparison. {@code fewer} > 0: this route has fewer than the recommended one. */
    private static void compare(StringBuilder text, int fewer, String singular, String plural,
                                String ifFewer, String ifMore) {
        if (fewer > 0) {
            text.append(' ').append(String.format(ifFewer, count(fewer, singular, plural)));
        } else if (fewer < 0) {
            text.append(' ').append(String.format(ifMore, count(-fewer, singular, plural)));
        }
    }

    private static Facts facts(RouteResult route, RoadGraph graph) {
        int lights = 0;
        int congested = 0;
        int incidents = 0;
        for (int edgeId : route.edgeIds()) {
            EdgeState state = graph.state(edgeId);
            if (state.signalDelaySec() > 0) {
                lights++;
            }
            if (state.congestion() >= CONGESTED_FROM) {
                congested++;
            }
            if (state.incidentFactor() > 1.0) {
                incidents++;
            }
        }
        return new Facts(route.seconds() / 60.0, route.meters() / 1000.0, lights, congested, incidents);
    }

    private static List<GeoPoint> path(RouteResult route, RoadGraph graph) {
        List<GeoPoint> points = new ArrayList<>();
        for (int edgeId : route.edgeIds()) {
            Edge edge = graph.edge(edgeId);
            if (points.isEmpty()) {
                points.add(point(graph.node(edge.from())));
            }
            points.add(point(graph.node(edge.to())));
        }
        return points;
    }

    private static List<Long> segmentIds(RouteResult route, RoadGraph graph) {
        return route.edgeIds().stream().map(id -> graph.edge(id).dbId()).toList();
    }

    private static GeoPoint point(Node node) {
        return new GeoPoint(node.lat(), node.lon());
    }

    private static String minutes(double value) {
        return String.format(SPANISH, "%.1f min", value);
    }

    private static String km(double value) {
        return String.format(SPANISH, "%.1f km", value);
    }

    private static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
