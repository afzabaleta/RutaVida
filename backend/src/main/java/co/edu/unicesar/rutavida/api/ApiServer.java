package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.UserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The web server of RutaVida (decision DT-06, Javalin): it serves the pages of
 * {@code frontend/} and the REST API of docs/api.md from the same address, so the web
 * calls {@code /api/...} without CORS.
 *
 * <p>Every protected route goes through {@link #secured}: it finds the session of the
 * {@code Authorization: Bearer} token, checks the permission of the route and runs the
 * handler with that session (so the controllers and the audit triggers see that user).
 *
 * <p>Routes: session (A12a), emergencies and catalogs (A12b). Dispatch, routes, tracking and
 * simulation are added when their controllers exist (K7).
 */
public final class ApiServer {

    public static final int DEFAULT_PORT = 8080;

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final String JSON = "application/json; charset=utf-8";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BAD_REQUEST = "La solicitud no tiene el formato esperado.";
    private static final String BAD_ID = "El identificador debe ser un número entero positivo.";

    /**
     * Second argument of {@link #secured}: a request made by the user counts as use of the
     * session. The queries the map makes by itself (tracking, traffic, alerts; A12b) pass
     * false, so an open tab does not keep the session alive (DT-08).
     */
    private static final boolean USER_ACTION = true;
    private static final boolean AUTOMATIC = false;

    private final WebSessionController sessions;
    private final CatalogController catalogs;
    private final EmergencyController emergencies;
    private final DashboardController dashboard;
    private final Path frontendDirectory;
    private final ObjectMapper json = JsonMapperFactory.create();
    private Javalin app;

    /**
     * @param frontendDirectory folder with index.html; null to serve only the API (tests)
     */
    public ApiServer(WebSessionController sessions, CatalogController catalogs,
                     EmergencyController emergencies, DashboardController dashboard, Path frontendDirectory) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.emergencies = Objects.requireNonNull(emergencies, "emergencies");
        this.dashboard = Objects.requireNonNull(dashboard, "dashboard");
        this.frontendDirectory = frontendDirectory;
    }

    public synchronized ApiServer start(int port) {
        if (app != null) {
            throw new IllegalStateException("The server is already running");
        }
        app = Javalin.create(this::configure).start(port);
        return this;
    }

    public synchronized void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    private void configure(JavalinConfig config) {
        config.startup.showJavalinBanner = false;
        if (frontendDirectory != null) {
            config.staticFiles.add(files -> {
                files.hostedPath = "/";
                files.directory = frontendDirectory.toAbsolutePath().normalize().toString();
                files.location = Location.EXTERNAL;
            });
        }

        // 4. Session (no permission: login is the only route without token)
        config.routes.post("/api/auth/login", this::login);
        config.routes.post("/api/auth/logout",
                ctx -> send(ctx, sessions.logout(ctx.header(AUTHORIZATION)), ApiStatus.OK));
        config.routes.get("/api/auth/session",
                ctx -> send(ctx, sessions.describe(ctx.header(AUTHORIZATION)), ApiStatus.OK));

        // 5. Emergencies
        config.routes.get("/api/emergency-types", secured("EMERGENCIAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, catalogs.emergencyTypes(), ApiStatus.OK)));
        config.routes.get("/api/emergencies", secured("EMERGENCIAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, emergencies.listActive(), ApiStatus.OK)));
        config.routes.post("/api/emergencies", secured("EMERGENCIAS", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, emergencies.register(readBody(ctx, EmergencyRequest.class)), ApiStatus.CREATED)));
        config.routes.get("/api/emergencies/{id}", secured("EMERGENCIAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, emergencies.find(pathId(ctx)), ApiStatus.OK)));
        config.routes.get("/api/emergencies/{id}/history", secured("EMERGENCIAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, emergencies.history(pathId(ctx)), ApiStatus.OK)));

        // 7. Resources: units, stations and hospitals
        config.routes.get("/api/vehicle-types", secured("UNIDADES", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, catalogs.vehicleTypes(), ApiStatus.OK)));
        config.routes.get("/api/units", secured("UNIDADES", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, catalogs.units(), ApiStatus.OK)));
        config.routes.get("/api/stations", secured("UNIDADES", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, catalogs.stations(), ApiStatus.OK)));
        config.routes.get("/api/hospitals", secured("HOSPITALES", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, catalogs.hospitals("true".equalsIgnoreCase(ctx.queryParam("all"))), ApiStatus.OK)));

        // 11. Dashboard. The panel refreshes it by itself, so it does not keep the session alive.
        config.routes.get("/api/dashboard/summary", secured("REPORTES", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, dashboard.summary(), ApiStatus.OK)));

        config.routes.exception(Exception.class, this::onError);
    }

    private void login(Context ctx) {
        LoginRequest request = readBody(ctx, LoginRequest.class);
        char[] password = request.password() == null ? null : request.password().toCharArray();
        send(ctx, sessions.login(request.username(), password), ApiStatus.OK);
    }

    /**
     * Wraps a handler: 401 without a valid token, 403 without the permission, otherwise the
     * handler runs with the session of the token. {@code countsAsUse} is false only for the
     * automatic queries of the map.
     */
    Handler secured(String module, String action, boolean countsAsUse, Handler handler) {
        return ctx -> {
            OperationResult<UserSession> session = sessions.authenticate(ctx.header(AUTHORIZATION), countsAsUse);
            if (!session.success()) {
                send(ctx, session, ApiStatus.OK);
                return;
            }
            OperationResult<Void> allowed = sessions.authorize(session.value(), module, action);
            if (!allowed.success()) {
                send(ctx, allowed, ApiStatus.OK);
                return;
            }
            sessions.runAs(session.value(), () -> handler.handle(ctx));
        };
    }

    /** The {id} of the URL as a positive number; anything else is a 400. */
    static long pathId(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            if (id <= 0) {
                throw new ValidationException(BAD_ID);
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ValidationException(BAD_ID);
        }
    }

    /** Reads the JSON body; an empty or malformed body is a 400. */
    <T> T readBody(Context ctx, Class<T> type) {
        String body = ctx.body();
        if (body == null || body.isBlank()) {
            throw new ValidationException(BAD_REQUEST);
        }
        try {
            T value = json.readValue(body, type);
            if (value == null) {
                throw new ValidationException(BAD_REQUEST);
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new ValidationException(BAD_REQUEST);
        }
    }

    /** Writes the result as {success, message, value} with its status code. */
    void send(Context ctx, OperationResult<?> result, int successStatus) {
        String body;
        try {
            body = json.writeValueAsString(ApiResponse.of(result));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        ctx.status(ApiStatus.of(result, successStatus));
        ctx.contentType(JSON);
        ctx.result(body);
    }

    /** Last line of defense: nothing technical reaches the web. */
    private void onError(Exception exception, Context ctx) {
        RuntimeException error = exception instanceof RuntimeException runtime
                ? runtime : new IllegalStateException(exception);
        OperationResult<Void> result = OperationResult.failure(error);
        if (result.error() == ErrorKind.UNEXPECTED || result.error() == ErrorKind.UNAVAILABLE) {
            LOG.log(Level.WARNING, "Request failed", exception);
        }
        try {
            send(ctx, result, ApiStatus.OK);
        } catch (RuntimeException writing) {
            LOG.log(Level.SEVERE, "Could not write the error response", writing);
            ctx.status(500);
        }
    }
}