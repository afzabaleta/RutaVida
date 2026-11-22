package co.edu.unicesar.rutavida.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorLogTest {

    private static final Logger LOG = Logger.getLogger(ErrorLogTest.class.getName());

    @TempDir
    Path folder;

    /** The handler is global (root logger): each test removes the one it added. */
    @AfterEach
    void removeTheFileHandler() {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            if (handler.getClass().getEnclosingClass() == ErrorLog.class) {
                root.removeHandler(handler);
                handler.close();
            }
        }
    }

    @Test
    void warningsAndErrorsGoToTheFileWithTheirStackTrace() throws IOException {
        Path file = ErrorLog.install(folder.resolve("logs"));

        LOG.log(Level.WARNING, "Dispatch operation failed", new IllegalStateException("ORA-12541 sin conexión"));
        LOG.severe("Could not write the error response");

        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(folder.resolve("logs").resolve("rutavida-errores.0.log"), file);
        assertTrue(text.contains("WARNING " + ErrorLogTest.class.getName() + " - Dispatch operation failed"), text);
        assertTrue(text.contains("java.lang.IllegalStateException: ORA-12541 sin conexión"), text);
        assertTrue(text.contains("at co.edu.unicesar.rutavida.util.ErrorLogTest"), "stack trace: " + text);
        assertTrue(text.contains("SEVERE " + ErrorLogTest.class.getName() + " - Could not write the error response"), text);
    }

    @Test
    void informationIsNotWritten() throws IOException {
        Path file = ErrorLog.install(folder);

        LOG.info("Grafo cargado: 7569 nodos");
        LOG.warning("Position skipped for unit 4");

        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(text.contains("Grafo cargado"), text);
        assertTrue(text.contains("Position skipped for unit 4"), text);
    }

    @Test
    void installingTwiceDoesNotDuplicateTheEntries() throws IOException {
        Path first = ErrorLog.install(folder);
        Path second = ErrorLog.install(folder);
        long handlers = Arrays.stream(Logger.getLogger("").getHandlers())
                .filter(handler -> handler.getClass().getEnclosingClass() == ErrorLog.class)
                .count();

        LOG.warning("una sola vez");

        assertEquals(first, second);
        assertEquals(1, handlers);
        String text = Files.readString(first, StandardCharsets.UTF_8);
        assertEquals(text.indexOf("una sola vez"), text.lastIndexOf("una sola vez"), text);
    }

    @Test
    void theTimeIsColombiaAndTheParametersAreFilledIn() {
        LogRecord entry = new LogRecord(Level.WARNING, "Route {0} could not be recalculated");
        entry.setParameters(new Object[] {42});
        entry.setLoggerName("co.edu.unicesar.rutavida.service.RouteMonitor");
        // 2026-11-21 20:00 UTC is 15:00 in Colombia (UTC-5, no daylight saving time)
        entry.setInstant(Instant.parse("2026-11-21T20:00:00Z"));

        String line = ErrorLog.format(entry);

        assertTrue(line.startsWith("2026-11-21 15:00:00 WARNING co.edu.unicesar.rutavida.service.RouteMonitor"
                + " - Route 42 could not be recalculated"), line);
    }
}
