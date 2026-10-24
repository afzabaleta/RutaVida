package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.osm.BoundingBox;
import co.edu.unicesar.rutavida.osm.OsmExtract;
import co.edu.unicesar.rutavida.osm.OsmParser;
import co.edu.unicesar.rutavida.osm.RoadNetwork;
import co.edu.unicesar.rutavida.osm.RoadNetworkBuilder;
import co.edu.unicesar.rutavida.osm.RoadNetworkSqlWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Development tool (it is not part of the final application): converts the .osm file
 * downloaded from Overpass into database/05_red_vial_valledupar.sql.
 *
 * <p>Arguments: {@code <archivo.osm> <salida.sql> <fecha-descarga AAAA-MM-DD>
 * [sur oeste norte este]}. Without the last four, it uses
 * {@link BoundingBox#VALLEDUPAR_DEMO}. Exit code 0 = OK, 1 = problem found.
 */
public final class OsmImporter {

    private OsmImporter() {
    }

    public static void main(String[] args) {
        if (args.length != 3 && args.length != 7) {
            System.err.println("Uso: OsmImporter <archivo.osm> <salida.sql> <fecha-descarga AAAA-MM-DD>"
                    + " [sur oeste norte este]");
            System.exit(1);
            return;
        }
        try {
            Path input = Path.of(args[0]);
            Path output = Path.of(args[1]);
            LocalDate downloadedOn = LocalDate.parse(args[2]);
            BoundingBox area = args.length == 7
                    ? new BoundingBox(Double.parseDouble(args[3]), Double.parseDouble(args[4]),
                    Double.parseDouble(args[5]), Double.parseDouble(args[6]))
                    : BoundingBox.VALLEDUPAR_DEMO;

            OsmExtract extract;
            try (InputStream in = Files.newInputStream(input)) {
                extract = new OsmParser().parse(in);
            }
            RoadNetwork network = new RoadNetworkBuilder(area).build(extract);
            try (Writer out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                new RoadNetworkSqlWriter().write(network, area, downloadedOn, out);
            }

            RoadNetwork.Summary summary = network.summary();
            System.out.println("[OK] Script generado: " + output.toAbsolutePath());
            System.out.println("     Vias en el archivo: " + summary.waysRead()
                    + " | usadas: " + summary.waysUsed());
            System.out.println("     Nodos: " + network.junctions().size()
                    + " | Tramos: " + network.links().size());
            System.out.println("     Vias sin nombre: " + summary.unnamedWays()
                    + " | con velocidad supuesta: " + summary.waysWithDefaultSpeed()
                    + " | pedazos descartados: " + summary.skippedPieces());
        } catch (NoSuchFileException e) {
            System.err.println("[ERROR] No existe el archivo: " + e.getMessage()
                    + " (la carpeta de trabajo debe ser backend/)");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo leer o escribir el archivo: " + e.getMessage());
            System.exit(1);
        } catch (DateTimeParseException e) {
            System.err.println("[ERROR] La fecha debe tener el formato AAAA-MM-DD, por ejemplo 2026-10-22");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
        }
    }
}