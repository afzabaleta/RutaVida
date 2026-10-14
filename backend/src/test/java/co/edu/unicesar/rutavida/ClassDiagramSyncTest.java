package co.edu.unicesar.rutavida;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps docs/class-diagram.md in sync with the code: every class, interface, enum or
 * record in src/main/java must be declared in the Mermaid class diagram. When someone
 * adds a class, this test reminds them to add it to the diagram in the same Pull Request.
 */
class ClassDiagramSyncTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path DIAGRAM = Path.of("..", "docs", "class-diagram.md");
    private static final Pattern DIAGRAM_CLASS = Pattern.compile("^\\s*class (\\w+)", Pattern.MULTILINE);

    @Test
    void everyTypeInTheCodeAppearsInTheClassDiagram() throws IOException {
        assertTrue(Files.isRegularFile(DIAGRAM), "Not found: " + DIAGRAM.toAbsolutePath());

        Set<String> inDiagram = DIAGRAM_CLASS.matcher(Files.readString(DIAGRAM, StandardCharsets.UTF_8))
                .results().map(m -> m.group(1)).collect(Collectors.toSet());

        List<String> missing;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            missing = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".java") && !name.equals("package-info.java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .filter(type -> !inDiagram.contains(type))
                    .sorted()
                    .toList();
        }
        assertTrue(missing.isEmpty(), "Add these types to docs/class-diagram.md: " + missing);
    }

    /** Small guard so the regex keeps matching the diagram format. */
    @Test
    void diagramDeclaresTheKnownInfrastructure() throws IOException {
        Matcher matcher = DIAGRAM_CLASS.matcher(Files.readString(DIAGRAM, StandardCharsets.UTF_8));
        Set<String> inDiagram = matcher.results().map(m -> m.group(1)).collect(Collectors.toSet());
        assertTrue(inDiagram.containsAll(Set.of("TransactionManager", "InMemoryEventBus", "EmergencyStatus")),
                "The class diagram format changed; update this test");
    }
}
