package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.AlertController;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
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
 * <p>Routes: session (A12a), emergencies and catalogs (A12b), dashboard (A14), road incidents
 * (A12c), alerts (A12f), dispatch and routes (A12d) and closing an emergency (A16). Tracking
 * and simulation are added when their controllers exist (K9, A12e).
 */
public final class ApiServer {

    public static final int DEFAULT_PORT = 8080;

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final String JSON = "application/json; charset=utf-8";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BAD_REQUEST = "La solicitud no tiene el formato esperado.";
    private static final String BAD_ID = "El identificador debe ser un número entero positivo.";
    private static final String BAD_STATUS = "El estado del incidente no es válido.";
    private static final String BAD_DISPATCH = "Indique la emergencia y la unidad (emergencyId y vehicleId).";
    private static final String BAD_MISSION_STATUS = "El estado de la misión no es válido.";

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
    private final IncidentController incidents;
    private final RoadMapController roadMap;
    private final AlertController alerts;
    private final DispatchController dispatch;
    private final Path frontendDirectory;
    private final ObjectMapper json = JsonMapperFactory.create();
    private Javalin app;

    /**
     * @param frontendDirectory folder with index.html; null to serve only the API (tests)
     */
    public ApiServer(WebSessionController sessions, CatalogController catalogs,
                     EmergencyController emergencies, DashboardController dashboard,
                     IncidentController incidents, RoadMapController roadMap, AlertController alerts,
                     DispatchController dispatch, Path frontendDirectory) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.emergencies = Objects.requireNonNull(emergencies, "emergencies");
        this.dashboard = Objects.requireNonNull(dashboard, "dashboard");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.roadMap = Objects.requireNonNull(roadMap, "roadMap");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
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

        // 6. Dispatch and routes (K7). The permission is checked here and again in DispatchController.
        config.routes.get("/api/emergencies/{id}/candidates", secured("DESPACHO", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, dispatch.candidates(pathId(ctx)), ApiStatus.OK)));
        config.routes.post("/api/routes/preview", secured("RUTAS", "GESTIONAR", USER_ACTION, ctx -> {
            DispatchRequest request = readDispatch(ctx);
            send(ctx, dispatch.preview(request.emergencyId(), request.vehicleId()), ApiStatus.OK);
        }));
        config.routes.post("/api/dispatches", secured("DESPACHO", "GESTIONAR", USER_ACTION, ctx -> {
            DispatchRequest request = readDispatch(ctx);
            send(ctx, roadMap.dispatchOnMap(dispatch.dispatch(request.emergencyId(), request.vehicleId())),
                    ApiStatus.CREATED);
        }));
        config.routes.get("/api/dispatches/{id}/routes", secured("RUTAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, roadMap.routesOnMap(dispatch.missionRoutes(pathId(ctx))), ApiStatus.OK)));
        config.routes.patch("/api/dispatches/{id}/status", secured("DESPACHO", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, dispatch.advance(pathId(ctx), readMissionStatus(ctx)), ApiStatus.OK)));
        config.routes.post("/api/emergencies/{id}/close", secured("DESPACHO", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, dispatch.close(pathId(ctx)), ApiStatus.OK)));

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

        // 9. Road incidents. The map refreshes the list by itself, so reading it does not
        // keep the session alive; reporting and validating do.
        config.routes.get("/api/incidents", secured("INCIDENTES", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, roadMap.incidentsOnMap(listIncidents(ctx)), ApiStatus.OK)));
        config.routes.post("/api/incidents", secured("INCIDENTES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, roadMap.incidentOnMap(incidents.report(readBody(ctx, IncidentRequest.class))),
                        ApiStatus.CREATED)));
        config.routes.patch("/api/incidents/{id}/status", secured("INCIDENTES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, roadMap.incidentOnMap(incidents.changeStatus(pathId(ctx), readStatus(ctx))),
                        ApiStatus.OK)));

        // 11. Alerts of the user of the session: any valid token, no permission (docs/api.md 3).
        // The bell refreshes them by itself, so reading them does not keep the session alive.
        config.routes.get("/api/alerts", authenticated(AUTOMATIC,
                ctx -> send(ctx, alerts.list("true".equalsIgnoreCase(ctx.queryParam("unread"))), ApiStatus.OK)));
        config.routes.patch("/api/alerts/{id}/read", authenticated(USER_ACTION,
                ctx -> send(ctx, alerts.markAsRead(pathId(ctx)), ApiStatus.OK)));

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

    /**
     * Like {@link #secured}, for the routes that only need a valid session ("Sesión" in
     * docs/api.md 3): 401 without a valid token, otherwise the handler runs with that session.
     */
    Handler authenticated(boolean countsAsUse, Handler handler) {
        return ctx -> {
            OperationResult<UserSession> session = sessions.authenticate(ctx.header(AUTHORIZATION), countsAsUse);
            if (!session.success()) {
                send(ctx, session, ApiStatus.OK);
                return;
            }
            sessions.runAs(session.value(), () -> handler.handle(ctx));
        };
    }

    /** GET /api/incidents: the open ones, or only those of ?status= (an unknown status is a 400). */
    private OperationResult<List<RoadIncident>> listIncidents(Context ctx) {
        String status = ctx.queryParam("status");
        if (status == null || status.isBlank()) {
            return incidents.listOpen();
        }
        try {
            return incidents.listByStatus(IncidentStatus.fromCode(status.trim()));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(BAD_STATUS);
        }
    }

    /** The new status of PATCH /api/incidents/{id}/status; without it, a 400. */
    private IncidentStatus readStatus(Context ctx) {
        IncidentStatus status = readBody(ctx, IncidentStatusRequest.class).status();
        if (status == null) {
            throw new ValidationException(BAD_STATUS);
        }
        return status;
    }

    /** The body of 6.2 and 6.3; without one of the two ids, a 400. */
    private DispatchRequest readDispatch(Context ctx) {
        DispatchRequest request = readBody(ctx, DispatchRequest.class);
        if (request.emergencyId() == null || request.vehicleId() == null
                || request.emergencyId() <= 0 || request.vehicleId() <= 0) {
            throw new ValidationException(BAD_DISPATCH);
        }
        return request;
    }

    /** The new status of PATCH /api/dispatches/{id}/status; without it, a 400. */
    private AssignmentStatus readMissionStatus(Context ctx) {
        AssignmentStatus status = readBody(ctx, AssignmentStatusRequest.class).status();
        if (status == null) {
            throw new ValidationException(BAD_MISSION_STATUS);
        }
        return status;
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