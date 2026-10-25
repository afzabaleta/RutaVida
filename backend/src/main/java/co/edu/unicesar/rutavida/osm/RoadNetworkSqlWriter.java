package co.edu.unicesar.rutavida.osm;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Writes the road network as the Oracle script database/05_red_vial_valledupar.sql.
 *
 * <ul>
 *   <li>Each NODO_VIAL row is identified by its {@code osm_id} and each TRAMO_VIAL row
 *       looks up its two nodes by {@code osm_id}, so the script does not depend on the
 *       values of the sequences.</li>
 *   <li>{@code WHERE NOT EXISTS} lets the script run twice without duplicating rows.</li>
 *   <li>Numbers always use a dot ({@link Locale#ROOT}), even on a computer in Spanish.</li>
 *   <li>Texts lose their accents (like 03_datos_valledupar.sql) so SQL*Plus on Windows
 *       does not damage them, and single quotes are doubled.</li>
 *   <li>A comment never goes after the ';' of a statement (SqlScriptStyleTest).</li>
 * </ul>
 */
public final class RoadNetworkSqlWriter {

    public static final String FILE_NAME = "05_red_vial_valledupar.sql";

    public void write(RoadNetwork network, BoundingBox area, LocalDate downloadedOn,
                      Appendable out) throws IOException {
        RoadNetwork.Summary summary = network.summary();

        line(out, "-- ============================================================");
        line(out, "-- RutaVida - Red vial de Valledupar (zona de la demostracion)");
        line(out, "-- Archivo: " + FILE_NAME);
        line(out, "-- GENERADO por OsmImporter. No se edita a mano: se vuelve a generar.");
        line(out, "--");
        line(out, "-- Datos: (c) OpenStreetMap contributors, licencia ODbL");
        line(out, "--        https://www.openstreetmap.org/copyright");
        line(out, "-- Descarga: Overpass API, " + downloadedOn);
        line(out, "-- Consulta: database/osm/valledupar_demo.overpassql");
        line(out, "-- Zona: sur " + number(area.south(), 6) + ", oeste " + number(area.west(), 6)
                + ", norte " + number(area.north(), 6) + ", este " + number(area.east(), 6));
        line(out, "--");
        line(out, "-- Supuestos (NO son datos de OpenStreetMap):");
        line(out, "--   * Vias principales sin maxspeed: "
                + number(RoadNetworkBuilder.MAIN_ROAD_DEFAULT_KMH, 0)
                + " km/h (Ley 2251 de 2022, maximo en vias urbanas)");
        line(out, "--   * Vias locales sin maxspeed: "
                + number(RoadNetworkBuilder.LOCAL_ROAD_DEFAULT_KMH, 0)
                + " km/h (Ley 2251 de 2022, zonas residenciales)");
        line(out, "--   * Vias sin nombre: '" + ascii(RoadNetworkBuilder.UNNAMED) + "'");
        line(out, "--   * Nombres sin tildes, igual que 03_datos_valledupar.sql");
        line(out, "--");
        line(out, "-- Resumen: " + network.junctions().size() + " nodos, "
                + network.links().size() + " tramos, a partir de " + summary.waysUsed()
                + " de " + summary.waysRead() + " vias del archivo.");
        line(out, "--   Vias sin nombre: " + summary.unnamedWays()
                + ". Vias con velocidad supuesta: " + summary.waysWithDefaultSpeed()
                + ". Pedazos descartados: " + summary.skippedPieces() + ".");
        line(out, "-- WHERE NOT EXISTS evita duplicados si el script se ejecuta dos veces.");
        line(out, "-- ============================================================");
        line(out, "");
        line(out, "SET DEFINE OFF");
        line(out, "SET FEEDBACK OFF");
        line(out, "");
        line(out, "-- 1. NODOS (" + network.junctions().size() + ")");
        for (RoadNetwork.Junction junction : network.junctions()) {
            line(out, "INSERT INTO nodo_vial (osm_id, referencia, latitud, longitud, tipo_nodo, estado) "
                    + "SELECT " + junction.osmId() + ", " + text(junction.reference()) + ", "
                    + number(junction.latitude(), 6) + ", " + number(junction.longitude(), 6) + ", '"
                    + junction.type().code() + "', 'A' FROM dual "
                    + "WHERE NOT EXISTS (SELECT 1 FROM nodo_vial WHERE osm_id = " + junction.osmId() + ");");
        }
        line(out, "");
        line(out, "COMMIT;");
        line(out, "");
        line(out, "-- 2. TRAMOS (" + network.links().size() + ")");
        for (RoadNetwork.Link link : network.links()) {
            line(out, "INSERT INTO tramo_vial (id_nodo_origen, id_nodo_destino, osm_way_id, nombre_via, "
                    + "distancia_m, velocidad_max_kmh) "
                    + "SELECT o.id_nodo, d.id_nodo, " + link.osmWayId() + ", " + text(link.roadName()) + ", "
                    + number(link.lengthMeters(), 2) + ", " + number(link.maxSpeedKmh(), 2) + " "
                    + "FROM nodo_vial o, nodo_vial d "
                    + "WHERE o.osm_id = " + link.fromOsmId() + " AND d.osm_id = " + link.toOsmId() + " "
                    + "AND NOT EXISTS (SELECT 1 FROM tramo_vial t WHERE t.id_nodo_origen = o.id_nodo "
                    + "AND t.id_nodo_destino = d.id_nodo AND t.osm_way_id = " + link.osmWayId() + ");");
        }
        line(out, "");
        line(out, "COMMIT;");
        line(out, "");
        line(out, "SET FEEDBACK ON");
        line(out, "SET DEFINE ON");
    }

    // ------------------------------------------------------------------ helpers

    private static void line(Appendable out, String text) throws IOException {
        out.append(text).append('\n');
    }

    static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** 'Text' with quotes doubled and without accents, or NULL. */
    static String text(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + ascii(value).replace("'", "''") + "'";
    }

    /** "Avenida Simón Bolívar" -> "Avenida Simon Bolivar"; control characters -> space. */
    static String ascii(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.replaceAll("[^\\x20-\\x7E]", " ").strip();
    }
}