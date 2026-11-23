package co.edu.unicesar.rutavida.app;

import co.edu.unicesar.rutavida.api.AdminControllers;
import co.edu.unicesar.rutavida.api.ApiServer;
import co.edu.unicesar.rutavida.api.GpsHub;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.util.ErrorLog;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts RutaVida for the browser: the pages of {@code frontend/} and the REST API on
 * {@code http://localhost:8080} (decision DT-06). Run it from IntelliJ with backend/ as
 * working directory, with Oracle running. An optional argument changes the port.
 *
 * <p>The road graph is loaded from Oracle when the server starts (dispatch and routes, A12d),
 * so without Oracle or without the road network it stops with a message.
 *
 * <p>Warnings and errors also go to {@code backend/logs/rutavida-errores.0.log} (ErrorLog), so
 * they can be read after the console is closed.
 */
public final class WebServer {

    /** Relative to the working folder, backend/ (IntelliJ and iniciar-servidor.bat / .sh). */
    static final String LOG_FOLDER = "logs";

    private WebServer() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ApiServer.DEFAULT_PORT;
        try {
            Path errors = ErrorLog.install(Path.of(LOG_FOLDER));
            System.out.println("[OK] Los errores también se guardan en " + errors);
        } catch (UncheckedIOException e) {
            // Without the file the server still works: the errors stay in the console
            System.out.println("[AVISO] " + e.getMessage() + ". Los errores solo se verán en la consola.");
        }
        Path frontend = Path.of("..", "frontend").toAbsolutePath().normalize();
        if (!Files.isRegularFile(frontend.resolve("index.html"))) {
            System.out.println("[AVISO] No se encontró " + frontend.resolve("index.html")
                    + ". Ejecute WebServer con backend/ como carpeta de trabajo. Solo se sirve la API.");
            frontend = null;
        }

        AppContext context = AppContext.create();
        DispatchController dispatch;
        try {
            dispatch = context.dispatchController();
        } catch (RuntimeException e) {
            System.out.println("[ERROR] No se pudo cargar la red vial desde Oracle. Revise que Oracle esté"
                    + " encendido y que se ejecutó database/05_red_vial_valledupar.sql.");
            System.out.println("Detalle: " + e.getMessage());
            return;
        }
        ApiServer server = new ApiServer(context.webSessionController(), context.catalogController(),
                context.emergencyController(), context.dashboardController(), context.reportController(),
                context.incidentController(),
                context.roadMapController(), context.alertController(), dispatch,
                new AdminControllers(context.userController(), context.fleetController(), context.hospitalController(),
                        context.auditController()),
                context.trackingController(), context.panicController(), context.citizenController(), frontend)
                .withSimulation(context.simulationController())
                .withGps(context.gpsController(), new GpsHub(context.webSessionController()));
        try {
            server.start(port);
        } catch (RuntimeException e) {
            System.out.println("[ERROR] No se pudo iniciar el servidor en el puerto " + port
                    + ". ¿Hay otro programa usándolo? Puede indicar otro puerto como argumento, por ejemplo 8081.");
            System.out.println("Detalle: " + e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            context.stopSimulation();
            server.stop();
        }, "rutavida-stop"));
        System.out.println("[OK] RutaVida está en http://localhost:" + port + "  (API en /api)");
        System.out.println("     Para detenerlo, use el botón Stop de IntelliJ.");
    }
}
