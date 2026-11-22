package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.model.GeoPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the places and routes of the demo as GeoJSON (RFC 7946) for the Leaflet map of
 * the web view. GeoJSON puts the longitude first: {@code [lon, lat]}.
 *
 * <p>Every feature carries {@code source}: where its data comes from. The map must show
 * it, so simulated or example data is never presented as real (project rule).
 */
public final class RouteGeoJsonWriter {

    private final String title;
    private final String note;
    private final List<String> features = new ArrayList<>();

    public RouteGeoJsonWriter(String title, String note) {
        this.title = required(title, "title");
        this.note = required(note, "note");
    }

    /** A point: kind = estacion, emergencia, hospital or unidad. */
    public RouteGeoJsonWriter addPlace(String kind, String name, GeoPoint point, String source) {
        if (point == null) {
            throw new IllegalArgumentException("point is required");
        }
        features.add(feature("{\"type\":\"Point\",\"coordinates\":" + coordinate(point) + "}",
                "\"kind\":" + text(required(kind, "kind"))
                        + ",\"name\":" + text(required(name, "name"))
                        + ",\"source\":" + text(required(source, "source"))));
        return this;
    }

    /** One route option of a leg (ida, traslado) and a scenario (inicial, congestion-simulada). */
    public RouteGeoJsonWriter addRoute(String leg, String scenario, RouteOption option, String source) {
        if (option == null) {
            throw new IllegalArgumentException("option is required");
        }
        features.add(feature(line(option.path()),
                "\"kind\":\"ruta\""
                        + ",\"leg\":" + text(required(leg, "leg"))
                        + ",\"scenario\":" + text(required(scenario, "scenario"))
                        + ",\"rank\":" + option.rank()
                        + ",\"type\":" + text(option.type().name())
                        + ",\"distanceKm\":" + number(option.distanceKm(), 6)
                        + ",\"estimatedMinutes\":" + number(option.estimatedMinutes(), 6)
                        + ",\"trafficLights\":" + option.trafficLights()
                        + ",\"congestedSegments\":" + option.congestedSegments()
                        + ",\"segments\":" + option.segmentIds().size()
                        + ",\"explanation\":" + text(option.explanation())
                        + ",\"source\":" + text(required(source, "source"))));
        return this;
    }

    /** A stretch of road with congestion (1.0 = free flow). */
    public RouteGeoJsonWriter addCongestion(String name, List<GeoPoint> path, double congestion, String source) {
        if (!(congestion >= 1.0)) {
            throw new IllegalArgumentException("congestion must be >= 1.0");
        }
        features.add(feature(line(path),
                "\"kind\":\"congestion\""
                        + ",\"name\":" + text(required(name, "name"))
                        + ",\"congestion\":" + number(congestion, 2)
                        + ",\"source\":" + text(required(source, "source"))));
        return this;
    }

    public int featureCount() {
        return features.size();
    }

    /** The FeatureCollection, one feature per line. */
    public String toJson() {
        return "{\"type\":\"FeatureCollection\",\"name\":" + text(title) + ",\"note\":" + text(note)
                + ",\"features\":[\n" + String.join(",\n", features) + "\n]}\n";
    }

    /** Writes the .geojson file (UTF-8). */
    public void writeGeoJson(Path file) throws IOException {
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }

    /**
     * Writes the same data as a script ({@code window.<variable> = {...};}). A page opened
     * with file:// cannot fetch() a .geojson, but it can load a script tag.
     */
    public void writeScript(Path file, String variable) throws IOException {
        if (variable == null || !variable.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("invalid JavaScript variable name: " + variable);
        }
        Files.writeString(file, "window." + variable + " = " + toJson().stripTrailing() + ";\n",
                StandardCharsets.UTF_8);
    }

    private static String feature(String geometry, String properties) {
        return "{\"type\":\"Feature\",\"geometry\":" + geometry + ",\"properties\":{" + properties + "}}";
    }

    private static String line(List<GeoPoint> path) {
        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException("a line needs at least two points");
        }
        List<String> points = new ArrayList<>();
        for (GeoPoint point : path) {
            points.add(coordinate(point));
        }
        return "{\"type\":\"LineString\",\"coordinates\":[" + String.join(",", points) + "]}";
    }

    private static String coordinate(GeoPoint point) {
        return "[" + number(point.longitude(), 6) + "," + number(point.latitude(), 6) + "]";
    }

    private static String number(double value, int decimals) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON has no NaN or Infinity");
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** JSON string with the characters that must be escaped. */
    static String text(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}