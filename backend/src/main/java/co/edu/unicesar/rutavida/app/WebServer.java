package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.api.ApiServer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts RutaVida for the browser: the pages of {@code frontend/} and the REST API on
 * {@code http://localhost:8080} (decision DT-06). Run it from IntelliJ with backend/ as
 * working directory, with Oracle running. An optional argument changes the port.
 */
public final class WebServer {

    private WebServer() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ApiServer.DEFAULT_PORT;
        Path frontend = Path.of("..", "frontend").toAbsolutePath().normalize();
        if (!Files.isRegularFile(frontend.resolve("index.html"))) {
            System.out.println("[AVISO] No se encontró " + frontend.resolve("index.html")
                    + ". Ejecute WebServer con backend/ como carpeta de trabajo. Solo se sirve la API.");
            frontend = null;
        }

        AppContext context = AppContext.create();
        ApiServer server = new ApiServer(context.webSessionController(), context.catalogController(),
                context.emergencyController(), context.dashboardController(), context.incidentController(),
                context.roadMapController(), context.alertController(), frontend);
        try {
            server.start(port);
        } catch (RuntimeException e) {
            System.out.println("[ERROR] No se pudo iniciar el servidor en el puerto " + port
                    + ". ¿Hay otro programa usándolo? Puede indicar otro puerto como argumento, por ejemplo 8081.");
            System.out.println("Detalle: " + e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "rutavida-stop"));
        System.out.println("[OK] RutaVida está en http://localhost:" + port + "  (API en /api)");
        System.out.println("     Para detenerlo, use el botón Stop de IntelliJ.");
    }
}