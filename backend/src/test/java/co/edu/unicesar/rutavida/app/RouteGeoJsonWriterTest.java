package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.dto.RouteOption;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test data only: the coordinates are points of Valledupar, the route is invented. */
class RouteGeoJsonWriterTest {

    private static final GeoPoint STATION = new GeoPoint(10.468580, -73.257590);
    private static final GeoPoint POPA = new GeoPoint(10.467172, -73.265099);

    private static RouteOption option(int rank) {
        return new RouteOption(rank, rank == 1 ? RouteType.MAIN : RouteType.ALTERNATIVE,
                List.of(STATION, POPA), List.of(501L, 502L), 1.339, 1.784, 2, 0,
                "Ruta recomendada: es la más rápida.");
    }

    private static RouteGeoJsonWriter writer() {
        return new RouteGeoJsonWriter("Prueba", "Datos de prueba");
    }

    @Test
    void coordinatesGoLongitudeFirst() {
        String json = writer().addPlace("estacion", "Estación", STATION, "Prueba").toJson();

        assertTrue(json.startsWith("{\"type\":\"FeatureCollection\",\"name\":\"Prueba\",\"note\":\"Datos de prueba\""));
        assertTrue(json.contains("{\"type\":\"Point\",\"coordinates\":[-73.257590,10.468580]}"), json);
        assertTrue(json.contains("\"kind\":\"estacion\",\"name\":\"Estación\",\"source\":\"Prueba\""), json);
    }

    @Test
    void aRouteCarriesItsLineNumbersAndSource() {
        String json = writer().addRoute("ida", "inicial", option(1), "Calculada").toJson();

        assertTrue(json.contains("\"coordinates\":[[-73.257590,10.468580],[-73.265099,10.467172]]"), json);
        assertTrue(json.contains("\"kind\":\"ruta\",\"leg\":\"ida\",\"scenario\":\"inicial\",\"rank\":1,"
                + "\"type\":\"MAIN\",\"distanceKm\":1.339000,\"estimatedMinutes\":1.784000,\"trafficLights\":2,"
                + "\"congestedSegments\":0,\"segments\":2,"
                + "\"explanation\":\"Ruta recomendada: es la más rápida.\",\"source\":\"Calculada\""), json);
    }

    @Test
    void featuresAreSeparatedByCommas() {
        RouteGeoJsonWriter writer = writer()
                .addPlace("emergencia", "Emergencia de EJEMPLO", POPA, "EJEMPLO")
                .addRoute("ida", "inicial", option(1), "Calculada")
                .addCongestion("Congestión SIMULADA", List.of(STATION, POPA), 4.0, "SIMULACION");
        String json = writer.toJson();

        assertEquals(3, writer.featureCount());
        assertEquals(3, json.split("\\{\"type\":\"Feature\"", -1).length - 1);
        assertTrue(json.contains("}},\n{\"type\":\"Feature\""));
        assertTrue(json.endsWith("}}\n]}\n"));
        assertTrue(json.contains("\"kind\":\"congestion\",\"name\":\"Congestión SIMULADA\",\"congestion\":4.00"));
    }

    @Test
    void textIsEscaped() {
        assertEquals("\"dice \\\"hola\\\" \\\\ fin\\nlinea\\u0001\"",
                RouteGeoJsonWriter.text("dice \"hola\" \\ fin\nlinea\u0001"));
    }

    @Test
    void invalidDataIsRejected() {
        RouteGeoJsonWriter writer = writer();
        assertThrows(IllegalArgumentException.class, () -> new RouteGeoJsonWriter(" ", "nota"));
        assertThrows(IllegalArgumentException.class, () -> writer.addPlace("estacion", "Estación", STATION, " "));
        assertThrows(IllegalArgumentException.class, () -> writer.addPlace("estacion", "Estación", null, "Prueba"));
        assertThrows(IllegalArgumentException.class, () -> writer.addRoute("ida", "inicial", null, "Prueba"));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addCongestion("Congestión", List.of(STATION), 2.0, "SIMULACION"));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addCongestion("Congestión", List.of(STATION, POPA), 0.5, "SIMULACION"));
        assertEquals(0, writer.featureCount(), "a rejected feature is not added");
    }

    @Test
    void writesTheGeoJsonAndTheScript() throws IOException {
        RouteGeoJsonWriter writer = writer().addPlace("estacion", "Estación", STATION, "Prueba");
        Path folder = Files.createTempDirectory("rutavida-geojson");
        Path geoJson = folder.resolve("rutas.geojson");
        Path script = folder.resolve("rutas.js");
        try {
            writer.writeGeoJson(geoJson);
            writer.writeScript(script, "RUTAVIDA_RUTAS_DEMO");

            assertEquals(writer.toJson(), Files.readString(geoJson, StandardCharsets.UTF_8));
            String js = Files.readString(script, StandardCharsets.UTF_8);
            assertTrue(js.startsWith("window.RUTAVIDA_RUTAS_DEMO = {\"type\":\"FeatureCollection\""), js);
            assertTrue(js.endsWith("]};\n"), js);
            assertThrows(IllegalArgumentException.class, () -> writer.writeScript(script, "no valido"));
        } finally {
            Files.deleteIfExists(geoJson);
            Files.deleteIfExists(script);
            Files.deleteIfExists(folder);
        }
    }
}