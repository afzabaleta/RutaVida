package co.edu.unicesar.rutavida.osm;

import co.edu.unicesar.rutavida.model.enums.NodeType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkSqlWriterTest {

    private static final LocalDate DOWNLOADED = LocalDate.of(2026, 10, 22);

    @Test
    void numbersUseADotEvenOnAComputerInSpanish() throws IOException {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("es-CO"));
            String sql = write(OsmSamples.network());

            assertTrue(sql.contains("10.002000, -72.999000"), sql);
            assertTrue(sql.contains("111.19, 30.00"), sql);
            assertFalse(sql.contains("10,002000"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void textsLoseTheirAccentsAndQuotesAreDoubled() throws IOException {
        RoadNetwork network = new RoadNetwork(
                List.of(new RoadNetwork.Junction(1, 10.0, -73.0, NodeType.INTERSECTION,
                                "Avenida Simón Bolívar x Calle 'La Ñ'"),
                        new RoadNetwork.Junction(2, 10.001, -73.0, NodeType.WAY_POINT, null)),
                List.of(new RoadNetwork.Link(1, 2, 77, "Avenida Simón Bolívar", 111.19, 50)),
                new RoadNetwork.Summary(1, 1, 0, 1, 0));

        String sql = write(network);

        assertTrue(sql.contains("'Avenida Simon Bolivar x Calle ''La N'''"), sql);
        assertTrue(sql.contains("SELECT 2, NULL, "), sql);
        assertTrue(sql.contains("77, 'Avenida Simon Bolivar', 111.19, 50.00"), sql);
        assertEquals("Via sin nombre (OSM)", RoadNetworkSqlWriter.ascii(RoadNetworkBuilder.UNNAMED));
    }

    @Test
    void everyStatementEndsItsLineAndTheScriptCanRunTwice() throws IOException {
        RoadNetwork network = OsmSamples.network();
        List<String> lines = write(network).lines().toList();

        List<String> inserts = lines.stream().filter(l -> l.startsWith("INSERT INTO")).toList();
        assertEquals(network.junctions().size() + network.links().size(), inserts.size());
        assertTrue(inserts.stream().allMatch(l -> l.endsWith(";") && l.contains("NOT EXISTS")));
        assertTrue(lines.stream().noneMatch(l -> l.matches(".*;\\s*--.*")));
        assertEquals(2, lines.stream().filter("COMMIT;"::equals).count());
        assertTrue(lines.contains("SET DEFINE OFF"));
        assertTrue(lines.stream().allMatch(l -> l.chars().allMatch(c -> c < 128)));
    }

    @Test
    void theHeaderSaysWhereTheDataComesFromAndWhatIsAssumed() throws IOException {
        String sql = write(OsmSamples.network());

        assertTrue(sql.contains("(c) OpenStreetMap contributors, licencia ODbL"));
        assertTrue(sql.contains("https://www.openstreetmap.org/copyright"));
        assertTrue(sql.contains("Descarga: Overpass API, 2026-10-22"));
        assertTrue(sql.contains("Supuestos (NO son datos de OpenStreetMap)"));
        assertTrue(sql.contains("Resumen: 8 nodos, 9 tramos, a partir de 4 de 6 vias del archivo."));
    }

    private static String write(RoadNetwork network) throws IOException {
        StringBuilder out = new StringBuilder();
        new RoadNetworkSqlWriter().write(network, OsmSamples.AREA, DOWNLOADED, out);
        return out.toString();
    }
}