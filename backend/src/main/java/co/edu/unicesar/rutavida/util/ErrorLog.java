package co.edu.unicesar.rutavida.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Keeps the warnings and errors of the server in files, so they are not lost when the
 * IntelliJ console is closed: after a test or the presentation, the team can read what failed.
 *
 * <p>The console keeps showing everything as before; the files get only WARNING and SEVERE,
 * with the stack trace. They rotate: at most {@link #FILES} files of {@link #MAX_BYTES} bytes,
 * {@code rutavida-errores.0.log} being the newest. The folder is ignored by Git (*.log).
 * Nothing secret is written: the code never logs passwords, tokens or the content of
 * db.properties.
 */
public final class ErrorLog {

    public static final String FILE_PATTERN = "rutavida-errores.%g.log";
    public static final int MAX_BYTES = 1_000_000;
    public static final int FILES = 5;

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Only to fill in the {0} parameters of a message, as the console does. */
    private static final Formatter PLAIN = new SimpleFormatter();

    private ErrorLog() {
    }

    /**
     * Adds the file handler to the root logger. Calling it again with the same folder does not
     * add a second handler.
     *
     * @param folder where the files go (created if it does not exist)
     * @return the path of the newest file
     * @throws UncheckedIOException if the folder or the file cannot be created
     */
    public static synchronized Path install(Path folder) {
        Path absolute = folder.toAbsolutePath().normalize();
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            if (handler instanceof ErrorFileHandler existing && existing.folder.equals(absolute)) {
                return existing.newestFile();
            }
        }
        try {
            Files.createDirectories(absolute);
            ErrorFileHandler handler = new ErrorFileHandler(absolute);
            root.addHandler(handler);
            return handler.newestFile();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el registro de errores en " + absolute, e);
        }
    }

    /** One entry: date and time of Colombia, level, where it happened, message and stack trace. */
    static String format(LogRecord entry) {
        StringBuilder text = new StringBuilder()
                .append(TIME.format(Instant.ofEpochMilli(entry.getMillis()).atZone(COLOMBIA)))
                .append(' ').append(entry.getLevel().getName())
                .append(' ').append(entry.getLoggerName() == null ? "-" : entry.getLoggerName())
                .append(" - ").append(PLAIN.formatMessage(entry))
                .append(System.lineSeparator());
        if (entry.getThrown() != null) {
            StringWriter trace = new StringWriter();
            entry.getThrown().printStackTrace(new PrintWriter(trace));
            text.append(trace);
        }
        return text.toString();
    }

    private static final class ErrorFileHandler extends FileHandler {

        private final Path folder;

        ErrorFileHandler(Path folder) throws IOException {
            super(folder.resolve(FILE_PATTERN).toString(), MAX_BYTES, FILES, true);
            this.folder = folder;
            setLevel(Level.WARNING);
            setEncoding(StandardCharsets.UTF_8.name());
            setFormatter(new Formatter() {
                @Override
                public String format(LogRecord entry) {
                    return ErrorLog.format(entry);
                }
            });
        }

        Path newestFile() {
            return folder.resolve(FILE_PATTERN.replace("%g", "0"));
        }

        @Override
        public synchronized void publish(LogRecord entry) {
            super.publish(entry);
            // Written at once: the server is usually stopped with Stop, without a clean exit
            flush();
        }
    }
}
