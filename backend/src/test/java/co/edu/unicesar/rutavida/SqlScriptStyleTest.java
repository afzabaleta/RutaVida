package co.edu.unicesar.rutavida;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL*Plus only ends a statement when ';' is the LAST thing on the line. A comment
 * after the semicolon ("...;   -- comment") makes Oracle receive the comment as part
 * of the statement and fail (ORA-02158 on 14/10/2026). This test forbids it.
 *
 * <p>Lines inside PL/SQL blocks (IF ... END IF; -- comment) are allowed because the
 * block ends with "/", not with ';'.
 */
class SqlScriptStyleTest {

    private static final Path DATABASE = Path.of("..", "database");
    private static final Pattern STATEMENT_WITH_TRAILING_COMMENT = Pattern.compile(
            "^\\s*(CREATE|ALTER|DROP|INSERT|UPDATE|DELETE|SELECT|COMMENT|GRANT)\\b.*;\\s*--.*$",
            Pattern.CASE_INSENSITIVE);

    @Test
    void noCommentAfterTheSemicolonOfAStatement() throws IOException {
        assertTrue(Files.isDirectory(DATABASE), "Folder not found: " + DATABASE.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> scripts = Files.list(DATABASE)) {
            for (Path script : scripts.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (STATEMENT_WITH_TRAILING_COMMENT.matcher(lines.get(i)).matches()) {
                        violations.add(script.getFileName() + ":" + (i + 1) + "  " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Move these comments to their own line:\n  " + String.join("\n  ", violations));
    }
}
