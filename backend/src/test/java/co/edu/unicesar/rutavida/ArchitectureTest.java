package co.edu.unicesar.rutavida;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the layer rules of docs/arquitectura-software.md by reading the imports of
 * every class in src/main/java. If someone imports java.sql in a service, or a DAO
 * implementation in a controller, this test fails and says where.
 *
 * <p>Must run with backend/ as working directory (default in Maven and IntelliJ).
 */
class ArchitectureTest {

    private static final String BASE = "co.edu.unicesar.rutavida";
    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final String APP = BASE + ".app";
    private static final String MODEL = BASE + ".model";
    private static final String DAO = BASE + ".dao";
    private static final String DAO_JDBC = BASE + ".dao.jdbc";
    private static final String SERVICE = BASE + ".service";
    private static final String GRAPH = BASE + ".graph";
    private static final String TRAFFIC = BASE + ".traffic";
    private static final String SIMULATION = BASE + ".simulation";
    private static final String CONTROLLER = BASE + ".controller";
    private static final String VIEW = BASE + ".view";
    private static final String OSM = BASE + ".osm";
    private static final String EXCEPTION = BASE + ".exception";
    private static final String UTIL = BASE + ".util";

    private static final String[] JDBC = {"java.sql", "javax.sql", "oracle"};
    private static final String[] UI = {"javax.swing", "java.awt"};

    private record SourceFile(Path path, String packageName, List<String> imports) {
    }

    /**
     * The model only holds data: it cannot validate (no exception, no util) nor reach other
     * layers. Business rules live in the service layer.
     */
    @Test
    void modelDependsOnNothingElseOfTheProject() {
        assertNoImports(inPackage(MODEL),
                concat(JDBC, UI, DAO, SERVICE, CONTROLLER, VIEW, GRAPH, TRAFFIC, SIMULATION, APP, EXCEPTION, UTIL));
    }

    @Test
    void daoInterfacesDoNotExposeJdbc() {
        assertNoImports(exactly(DAO), concat(JDBC, SERVICE, CONTROLLER, VIEW, DAO_JDBC));
    }

    @Test
    void daoImplementationsDoNotCallUpperLayers() {
        assertNoImports(inPackage(DAO_JDBC), concat(UI, SERVICE, CONTROLLER, VIEW, SIMULATION, APP));
    }

    @Test
    void servicesDoNotKnowJdbcNorTheUserInterface() {
        assertNoImports(inPackage(SERVICE), concat(JDBC, UI, DAO_JDBC, CONTROLLER, VIEW, APP));
    }

    @Test
    void routingEngineIsPureJava() {
        assertNoImports(inPackage(GRAPH).or(inPackage(TRAFFIC)),
                concat(JDBC, UI, DAO, SERVICE, CONTROLLER, VIEW, SIMULATION, APP));
    }

    @Test
    void controllersDoNotTouchPersistenceNorTheUserInterface() {
        assertNoImports(inPackage(CONTROLLER), concat(JDBC, UI, DAO, VIEW, APP));
    }
    @Test
    void osmImporterOnlyReadsAFileAndWritesSql() {
        assertNoImports(inPackage(OSM),
                concat(JDBC, UI, DAO, SERVICE, CONTROLLER, VIEW, GRAPH, TRAFFIC, SIMULATION, APP));
    }

    @Test
    void viewOnlyTalksToControllers() {
        assertNoImports(inPackage(VIEW), concat(JDBC, DAO, SERVICE, GRAPH, TRAFFIC, SIMULATION));
    }

    @Test
    void onlyTheCompositionRootKnowsDaoImplementations() {
        assertNoImports(inPackage(DAO_JDBC).negate().and(inPackage(APP).negate()), new String[]{DAO_JDBC});
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNoImports(Predicate<SourceFile> which, String[] forbidden) {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : sources()) {
            if (!which.test(file)) {
                continue;
            }
            for (String imported : file.imports()) {
                for (String prefix : forbidden) {
                    if (imported.equals(prefix) || imported.startsWith(prefix + ".")) {
                        violations.add(SOURCES.relativize(file.path()) + " imports " + imported);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Architecture rule broken:\n  " + String.join("\n  ", violations));
    }

    private static Predicate<SourceFile> inPackage(String name) {
        return file -> file.packageName().equals(name) || file.packageName().startsWith(name + ".");
    }

    private static Predicate<SourceFile> exactly(String name) {
        return file -> file.packageName().equals(name);
    }

    private static String[] concat(Object... parts) {
        List<String> all = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof String[] array) {
                all.addAll(List.of(array));
            } else {
                all.add((String) part);
            }
        }
        return all.toArray(String[]::new);
    }

    private static List<SourceFile> sources() {
        assertTrue(Files.isDirectory(SOURCES),
                "Run the tests with backend/ as working directory; not found: " + SOURCES.toAbsolutePath());
        try (Stream<Path> paths = Files.walk(SOURCES)) {
            return paths.filter(p -> p.toString().endsWith(".java")).map(ArchitectureTest::parse).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SourceFile parse(Path path) {
        try {
            String packageName = "";
            List<String> imports = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("package ")) {
                    packageName = trimmed.substring(8, trimmed.indexOf(';')).trim();
                } else if (trimmed.startsWith("import ")) {
                    String name = trimmed.substring(7, trimmed.indexOf(';')).trim();
                    imports.add(name.startsWith("static ") ? name.substring(7).trim() : name);
                }
            }
            return new SourceFile(path, packageName, imports);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
