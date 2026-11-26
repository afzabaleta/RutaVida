package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.CitizenController;
import co.edu.unicesar.rutavida.controller.AlertController;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.PanicController;
import co.edu.unicesar.rutavida.controller.ReportController;
import co.edu.unicesar.rutavida.controller.SimulationController;
import co.edu.unicesar.rutavida.controller.TrackingController;
import co.edu.unicesar.rutavida.controller.GpsController;
import co.edu.unicesar.rutavida.controller.DriverOperationController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.dto.AuditQuery;
import co.edu.unicesar.rutavida.dto.CitizenRequest;
import co.edu.unicesar.rutavida.dto.DispatchResult;
import co.edu.unicesar.rutavida.dto.DriverMission;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.HospitalRequest;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.dto.GpsPositionRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.StationRequest;
import co.edu.unicesar.rutavida.dto.UserRequest;
import co.edu.unicesar.rutavida.dto.VehicleRequest;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.EmergencyAssignment;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.GlobalHeadersConfig.ReferrerPolicy;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
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
 * (A12c), alerts (A12f), dispatch and routes (A12d), closing an emergency (A16),
 * administration of users, units, stations and hospitals (A17), audit (A18), tracking
 * (A12e), panic button (A20b, with accounts since DT-13), citizen registration and the operations
 * report (A23), and the simulation with its traffic layer (K9, A12e), added through
 * {@link #withSimulation}.
 */
public final class ApiServer {

    public static final int DEFAULT_PORT = 8080;
    static final String CORS_ORIGINS_ENV = "RUTAVIDA_CORS_ORIGINS";
    private static final List<String> LOCAL_CORS_ORIGINS = List.of(
            "http://localhost:8080", "http://127.0.0.1:8080");

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final String JSON = "application/json; charset=utf-8";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BAD_REQUEST = "La solicitud no tiene el formato esperado.";
    private static final String BAD_ID = "El identificador debe ser un número entero positivo.";
    private static final String BAD_STATUS = "El estado del incidente no es válido.";
    private static final String BAD_DISPATCH = "Indique la emergencia y la unidad (emergencyId y vehicleId).";
    private static final String BAD_MISSION_STATUS = "El estado de la misión no es válido.";
    private static final String BAD_COORDINATE = "Indique el punto con lat y lon (grados decimales).";
    private static final String TRANSFER_WITHOUT_HOSPITAL =
            "Para el traslado elija el hospital de destino (hospitalId).";
    private static final String HOSPITAL_WITHOUT_TRANSFER =
            "El hospital (hospitalId) solo se envía con el estado TRASLADANDO.";
    private static final String BAD_RECORD_STATUS = "El estado debe ser A (activo) o I (inactivo).";
    private static final String BAD_UNIT_STATUS = "El estado de la unidad no es válido.";
    private static final String BAD_PASSWORD = "Escriba la contraseña actual y la nueva.";
    private static final String BAD_DATE = "La fecha debe tener la forma 2026-11-14 o 2026-11-14T08:30.";
    private static final String BAD_DAY = "La fecha debe tener la forma 2026-11-14.";
    private static final String BAD_LIMIT = "El límite debe ser un número entero.";
    private static final String BAD_RESULT = "El resultado debe ser EXITO o FALLO.";
    private static final String BAD_PANIC_STATUS = "Una alerta de pánico solo se puede marcar DESCARTADO.";
    private static final String BAD_PANIC_FILTER = "El estado del reporte de pánico no es válido.";
    private static final String BAD_TRAFFIC =
            "Indique el tramo y el nivel de tráfico (segmentId y level: FLUIDO, MODERADO o CONGESTIONADO).";
    private static final String BAD_LEVEL = "El nivel debe ser MODERADO o CONGESTIONADO.";
    static final String NO_SIMULATION = "La simulación no está disponible en este servidor.";
    private static final String TOO_MANY_PANIC =
            "Se enviaron demasiadas alertas desde esta conexión. Si la emergencia sigue, llame al 123.";

    private static final String TOO_MANY_REGISTRATIONS =
            "Se crearon demasiadas cuentas desde esta conexión. Intente más tarde.";

    static final String TOO_MANY_LOGINS =
            "Demasiados intentos fallidos de inicio de sesión. Espere 10 minutos antes de intentarlo de nuevo.";

    /** Panic button: reports per network address in 10 minutes (RN-13 limits each person to 3). */
    static final int PANIC_PER_ADDRESS = 20;
    static final Duration PANIC_WINDOW = Duration.ofMinutes(10);

    /**
     * Failed logins (wrong user or password) allowed in {@link #LOGIN_WINDOW}: per account and
     * network address, and per network address for any account. Only failures count, so a person
     * who types the password well is never stopped; a successful login clears its account.
     */
    static final int LOGIN_FAILURES_PER_ACCOUNT = 5;
    static final int LOGIN_FAILURES_PER_ADDRESS = 30;
    static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);

    /** Citizen accounts per network address in an hour (CU-23): stops scripts that create accounts. */
    static final int REGISTRATIONS_PER_ADDRESS = 10;
    static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

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
    private final ReportController reports;
    private final IncidentController incidents;
    private final RoadMapController roadMap;
    private final AlertController alerts;
    private final DispatchController dispatch;
    private final AdminControllers admin;
    private final TrackingController tracking;
    private final PanicController panic;
    private final CitizenController citizens;
    private final RequestRateLimiter panicLimiter;
    private final RequestRateLimiter registrationLimiter;
    private final RequestRateLimiter accountLoginFailures;
    private final RequestRateLimiter addressLoginFailures;
    private final Path frontendDirectory;
    private final ObjectMapper json = JsonMapperFactory.create();
    private SimulationController simulation;
    private GpsController gps;
    private GpsHub gpsHub;
    private DriverOperationController driverOperations;
    private Javalin app;

    /**
     * @param frontendDirectory folder with index.html; null to serve only the API (tests)
     */
    public ApiServer(WebSessionController sessions, CatalogController catalogs,
                     EmergencyController emergencies, DashboardController dashboard, ReportController reports,
                     IncidentController incidents, RoadMapController roadMap, AlertController alerts,
                     DispatchController dispatch, AdminControllers admin, TrackingController tracking,
                     PanicController panic, CitizenController citizens, Path frontendDirectory) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.emergencies = Objects.requireNonNull(emergencies, "emergencies");
        this.dashboard = Objects.requireNonNull(dashboard, "dashboard");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.roadMap = Objects.requireNonNull(roadMap, "roadMap");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.admin = Objects.requireNonNull(admin, "admin");
        this.tracking = Objects.requireNonNull(tracking, "tracking");
        this.panic = Objects.requireNonNull(panic, "panic");
        this.citizens = Objects.requireNonNull(citizens, "citizens");
        this.panicLimiter = new RequestRateLimiter(PANIC_PER_ADDRESS, PANIC_WINDOW, Clock.systemUTC());
        this.registrationLimiter = new RequestRateLimiter(
                REGISTRATIONS_PER_ADDRESS, REGISTRATION_WINDOW, Clock.systemUTC());
        this.accountLoginFailures = new RequestRateLimiter(
                LOGIN_FAILURES_PER_ACCOUNT, LOGIN_WINDOW, Clock.systemUTC());
        this.addressLoginFailures = new RequestRateLimiter(
                LOGIN_FAILURES_PER_ADDRESS, LOGIN_WINDOW, Clock.systemUTC());
        this.frontendDirectory = frontendDirectory;
    }

    /**
     * Adds the simulation (CU-19, docs/api.md 8.2 and 10), before {@link #start}. Without it,
     * those routes answer 503: the tests that do not need the simulation leave it out.
     */
    public synchronized ApiServer withSimulation(SimulationController simulation) {
        if (app != null) {
            throw new IllegalStateException("The server is already running");
        }
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        return this;
    }

    /** Adds real phone GPS reporting and the native WebSocket used by live-map viewers. */
    public synchronized ApiServer withGps(GpsController gps, GpsHub gpsHub) {
        if (app != null) {
            throw new IllegalStateException("The server is already running");
        }
        this.gps = Objects.requireNonNull(gps, "gps");
        this.gpsHub = Objects.requireNonNull(gpsHub, "gpsHub");
        return this;
    }

    /** Adds the driver-only arrival and attention actions used by Compartir ubicación. */
    public synchronized ApiServer withDriverOperations(DriverOperationController driverOperations) {
        if (app != null) {
            throw new IllegalStateException("The server is already running");
        }
        this.driverOperations = Objects.requireNonNull(driverOperations, "driverOperations");
        return this;
    }

    public synchronized ApiServer start(int port) {
        if (app != null) {
            throw new IllegalStateException("The server is already running");
        }
        // Render requires the web service to listen on all network interfaces.
        app = Javalin.create(this::configure).start("0.0.0.0", port);
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
        configureCors(config);
        config.bundledPlugins.enableGlobalHeaders(headers -> {
            headers.xContentTypeOptionsNoSniff();
            headers.xFrameOptions("DENY");
            headers.referrerPolicy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN);
            headers.getHeaders().put("Permissions-Policy", "geolocation=(self)");
        });
        if (frontendDirectory != null) {
            config.staticFiles.add(files -> {
                files.hostedPath = "/";
                files.directory = frontendDirectory.toAbsolutePath().normalize().toString();
                files.location = Location.EXTERNAL;
            });
        }

        // Health check for Render. The full application only starts after Oracle and the road graph load.
        config.routes.get("/health", ctx -> {
            ctx.contentType(JSON);
            ctx.header("Cache-Control", "no-store");
            ctx.result("{\"status\":\"ok\"}");
        });

        // 4. Session (no permission: login is the only route without token)
        config.routes.post("/api/auth/login", this::login);
        config.routes.post("/api/auth/logout",
                ctx -> send(ctx, sessions.logout(ctx.header(AUTHORIZATION)), ApiStatus.OK));
        config.routes.get("/api/auth/session",
                ctx -> send(ctx, sessions.describe(ctx.header(AUTHORIZATION)), ApiStatus.OK));

        // 5. Emergencies
        config.routes.get("/api/emergency-types", secured("EMERGENCIAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, catalogs.emergencyTypes(), ApiStatus.OK)));
        // Without dates: the open emergencies (CU-07). With from or to: every emergency reported in
        // that period, closed ones too, for the history page (CU-15, D10).
        config.routes.get("/api/emergencies", secured("EMERGENCIAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, hasPeriod(ctx)
                        ? emergencies.listByPeriod(dateParam(ctx, "from"), dateParam(ctx, "to"))
                        : emergencies.listActive(), ApiStatus.OK)));
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
            var result = dispatch.dispatch(request.emergencyId(), request.vehicleId());
            if (result.success()) broadcastMission(result.value().assignment().getVehicleId());
            send(ctx, roadMap.dispatchOnMap(result), ApiStatus.CREATED);
        }));
        config.routes.get("/api/dispatches/{id}/routes", secured("RUTAS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, roadMap.routesOnMap(dispatch.missionRoutes(pathId(ctx))), ApiStatus.OK)));
        config.routes.get("/api/dispatches/{id}/hospitals", secured("DESPACHO", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, dispatch.hospitalsFor(pathId(ctx)), ApiStatus.OK)));
        config.routes.patch("/api/dispatches/{id}/status", secured("DESPACHO", "GESTIONAR", USER_ACTION, ctx -> {
            var result = advanceMission(pathId(ctx), readBody(ctx, AssignmentStatusRequest.class));
            if (result.success()) broadcastMission(result.value().getVehicleId());
            send(ctx, result, ApiStatus.OK);
        }));
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

        // 8. Live map (A12e). The map asks every 5 seconds by itself (DT-07), so it does not keep
        // the session alive (DT-08).
        config.routes.get("/api/tracking", secured("EMERGENCIAS", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, tracking.snapshot(), ApiStatus.OK)));
        if (gps != null) {
            config.routes.get("/api/gps/unit", secured(GpsController.MODULE, GpsController.REPORT, USER_ACTION,
                    ctx -> send(ctx, gps.myUnit(), ApiStatus.OK)));
            config.routes.post("/api/gps/position", secured(GpsController.MODULE, GpsController.REPORT,
                    AUTOMATIC, ctx -> {
                        var result = gps.report(readBody(ctx, GpsPositionRequest.class));
                        if (result.success()) {
                            gpsHub.broadcast(result.value());
                        }
                        send(ctx, result, ApiStatus.OK);
                    }));
            if (driverOperations != null) {
                config.routes.get("/api/gps/mission", secured(GpsController.MODULE, GpsController.REPORT,
                        AUTOMATIC, ctx -> send(ctx, driverOperations.current(), ApiStatus.OK)));
                config.routes.get("/api/gps/mission/routes", secured(GpsController.MODULE, GpsController.REPORT,
                        AUTOMATIC, ctx -> send(ctx, roadMap.routesOnMap(driverOperations.routes()), ApiStatus.OK)));
                config.routes.post("/api/gps/session/start", secured(DriverOperationController.MODULE,
                        DriverOperationController.OPERATE, USER_ACTION,
                        ctx -> sendDriverMission(ctx, driverOperations.startGps())));
                config.routes.post("/api/gps/session/stop", secured(GpsController.MODULE,
                        GpsController.REPORT, USER_ACTION,
                        ctx -> sendDriverMission(ctx, driverOperations.stopGps())));
                config.routes.post("/api/gps/mission/arrival", secured(DriverOperationController.MODULE,
                        DriverOperationController.OPERATE, USER_ACTION,
                        ctx -> sendDriverMission(ctx, driverOperations.confirmArrival())));
                config.routes.get("/api/gps/mission/{id}/hospitals", secured(DriverOperationController.MODULE,
                        DriverOperationController.OPERATE, USER_ACTION,
                        ctx -> send(ctx, driverOperations.transferHospitals(pathId(ctx)), ApiStatus.OK)));
                config.routes.post("/api/gps/mission/{id}/transfer", secured(DriverOperationController.MODULE,
                        DriverOperationController.OPERATE, USER_ACTION, ctx -> {
                            Long hospitalId = readBody(ctx, DriverTransferRequest.class).hospitalId();
                            if (hospitalId == null || hospitalId <= 0) {
                                throw new ValidationException(TRANSFER_WITHOUT_HOSPITAL);
                            }
                            sendDriverMission(ctx, driverOperations.startTransfer(pathId(ctx), hospitalId));
                        }));
                config.routes.post("/api/gps/mission/finish", secured(DriverOperationController.MODULE,
                        DriverOperationController.OPERATE, USER_ACTION,
                        ctx -> sendDriverMission(ctx, driverOperations.finishAttention())));
            }
            config.routes.ws("/ws/gps", ws -> {
                ws.onConnect(context -> {
                    String origin = context.header("Origin");
                    if (origin != null && !corsOrigins(System.getenv(CORS_ORIGINS_ENV)).contains(origin)) {
                        context.closeSession(1008, "Origen no autorizado");
                    }
                });
                ws.onMessage(gpsHub::message);
                ws.onClose(gpsHub::close);
            });
        }
        // The traffic layer (8.2): the map asks every 15 seconds by itself, like the tracking.
        config.routes.get("/api/traffic/segments", secured("TRAFICO", "CONSULTAR", AUTOMATIC,
                ctx -> sendSimulation(ctx, controller -> controller.trafficSegments(levelParam(ctx)))));

        // 10. Simulation (CU-19, K9). The control panel refreshes the state by itself; starting,
        // stopping and forcing the traffic are actions of the user.
        config.routes.get("/api/simulation", secured("TRAFICO", "CONSULTAR", AUTOMATIC,
                ctx -> sendSimulation(ctx, SimulationController::status)));
        config.routes.post("/api/simulation/start", secured("TRAFICO", "GESTIONAR", USER_ACTION,
                ctx -> sendSimulation(ctx, SimulationController::start)));
        config.routes.post("/api/simulation/stop", secured("TRAFICO", "GESTIONAR", USER_ACTION,
                ctx -> sendSimulation(ctx, SimulationController::stop)));
        config.routes.post("/api/simulation/traffic", secured("TRAFICO", "GESTIONAR", USER_ACTION, ctx -> {
            TrafficRequest request = readTraffic(ctx);
            sendSimulation(ctx, controller -> controller.forceTraffic(request.segmentId(), request.level()));
        }));

        // 11. Dashboard. The panel refreshes it by itself, so it does not keep the session alive.
        config.routes.get("/api/dashboard/summary", secured("REPORTES", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, dashboard.summary(), ApiStatus.OK)));
        // Operations report of a period (A23, RF-11): asked by the user, so it keeps the session alive.
        config.routes.get("/api/reports/operations", secured("REPORTES", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, reports.operations(dateParam(ctx, "from"), dateParam(ctx, "to")), ApiStatus.OK)));

        // 9. Road incidents. The map refreshes the list by itself, so reading it does not
        // keep the session alive; reporting and validating do.
        config.routes.get("/api/incidents", secured("INCIDENTES", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, roadMap.incidentsOnMap(listIncidents(ctx)), ApiStatus.OK)));
        config.routes.get("/api/road/segments/nearest", secured("RED_VIAL", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, roadMap.nearestSegment(coordinate(ctx, "lat", 90), coordinate(ctx, "lon", 180)),
                        ApiStatus.OK)));
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

        // 15. Administration (A17). The permission is checked here and again in each controller.
        config.routes.get("/api/users", secured("USUARIOS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, admin.users().list(), ApiStatus.OK)));
        config.routes.post("/api/users", secured("USUARIOS", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.users().create(readBody(ctx, UserRequest.class)), ApiStatus.CREATED)));
        config.routes.patch("/api/users/{id}/status", secured("USUARIOS", "GESTIONAR", USER_ACTION, ctx -> {
            long id = pathId(ctx);
            send(ctx, readRecordStatus(ctx) == RecordStatus.ACTIVE
                    ? admin.users().activate(id) : admin.users().deactivate(id), ApiStatus.OK);
        }));
        config.routes.get("/api/roles", secured("USUARIOS", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, admin.users().roles(), ApiStatus.OK)));
        config.routes.post("/api/auth/password", authenticated(USER_ACTION, this::changePassword));

        config.routes.post("/api/units", secured("UNIDADES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.fleet().registerVehicle(readBody(ctx, VehicleRequest.class)), ApiStatus.CREATED)));
        config.routes.put("/api/units/{id}", secured("UNIDADES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.fleet().updateVehicle(pathId(ctx), readBody(ctx, VehicleRequest.class)),
                        ApiStatus.OK)));
        config.routes.patch("/api/units/{id}/status", secured("UNIDADES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.fleet().changeVehicleStatus(pathId(ctx), readUnitStatus(ctx)), ApiStatus.OK)));
        config.routes.post("/api/stations", secured("UNIDADES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.fleet().registerStation(readBody(ctx, StationRequest.class)), ApiStatus.CREATED)));
        config.routes.put("/api/stations/{id}", secured("UNIDADES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.fleet().updateStation(pathId(ctx), readBody(ctx, StationRequest.class)),
                        ApiStatus.OK)));
        config.routes.patch("/api/stations/{id}/status", secured("UNIDADES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.fleet().changeStationStatus(pathId(ctx), readRecordStatus(ctx)), ApiStatus.OK)));

        // Audit (CU-21, A18): who did what and when, with filters (docs/api.md 15.13).
        config.routes.get("/api/audit", secured("AUDITORIA", "CONSULTAR", USER_ACTION,
                ctx -> send(ctx, admin.audit().search(readAuditQuery(ctx)), ApiStatus.OK)));

        config.routes.post("/api/hospitals", secured("HOSPITALES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.hospitals().register(readBody(ctx, HospitalRequest.class)), ApiStatus.CREATED)));
        config.routes.put("/api/hospitals/{id}", secured("HOSPITALES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.hospitals().update(pathId(ctx), readBody(ctx, HospitalRequest.class)),
                        ApiStatus.OK)));
        config.routes.patch("/api/hospitals/{id}/status", secured("HOSPITALES", "GESTIONAR", USER_ACTION,
                ctx -> send(ctx, admin.hospitals().changeStatus(pathId(ctx), readRecordStatus(ctx)), ApiStatus.OK)));

        // 16. Panic button (CU-22, A20b). Reporting is public: anyone presses it without an account,
        // and a police officer with a session validates the alert alone (docs/api.md 16).
        // DT-13: only a registered person presses the button (citizen, police or administrator).
        config.routes.post("/api/panic", secured("PANICO", "REPORTAR", USER_ACTION, this::reportPanic));
        // CU-23: a person creates the citizen account of the button; public, limited per address.
        config.routes.post("/api/citizens", this::registerCitizen);
        // The types for the button page (A20c): public, so the page fills its form while it checks
        // the account and a citizen can say it is a fire. Read only, no personal data.
        config.routes.get("/api/panic/emergency-types", ctx -> send(ctx, catalogs.emergencyTypes(), ApiStatus.OK));
        // The panel map refreshes the reports by itself, so reading them does not keep the session alive.
        config.routes.get("/api/panic/reports", secured("PANICO", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, panic.listRecent(readPanicFilter(ctx)), ApiStatus.OK)));
        config.routes.patch("/api/panic/reports/{id}/status", secured("PANICO", "GESTIONAR", USER_ACTION, ctx -> {
            long id = pathId(ctx);
            readDiscard(ctx);
            send(ctx, panic.discard(id), ApiStatus.OK);
        }));

        config.routes.exception(Exception.class, this::onError);
    }

    /**
     * Enables cross-origin API calls only from the configured frontend origins. The browser sends
     * bearer tokens explicitly, so the API never enables credentialed wildcard CORS.
     */
    private static void configureCors(JavalinConfig config) {
        List<String> origins = corsOrigins(System.getenv(CORS_ORIGINS_ENV));
        config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
            rule.path = "/api/*";
            rule.maxAge = 600;
            origins.forEach(rule::allowHost);
        }));
    }

    /** Parses the comma-separated production allow-list, or local origins when it is not set. */
    static List<String> corsOrigins(String configured) {
        if (configured == null || configured.isBlank()) {
            return LOCAL_CORS_ORIGINS;
        }
        List<String> origins = new ArrayList<>();
        for (String candidate : configured.split(",")) {
            String origin = normalizeOrigin(candidate.trim());
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        if (origins.isEmpty()) {
            throw new IllegalArgumentException(CORS_ORIGINS_ENV + " must contain at least one HTTPS origin.");
        }
        return Collections.unmodifiableList(origins);
    }

    private static String normalizeOrigin(String candidate) {
        try {
            URI uri = new URI(candidate);
            boolean http = "http".equalsIgnoreCase(uri.getScheme());
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if ((!http && !https) || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null, uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException | IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid origin in " + CORS_ORIGINS_ENV
                    + ": use an exact http(s) origin without a path, for example https://rutavida.vercel.app.");
        }
    }

    /**
     * POST /api/auth/login, against password guessing: after {@link #LOGIN_FAILURES_PER_ACCOUNT}
     * failures of one account from one address (or {@link #LOGIN_FAILURES_PER_ADDRESS} of any
     * account) in {@link #LOGIN_WINDOW}, it answers 409 without checking the password.
     */
    private void login(Context ctx) {
        LoginRequest request = readBody(ctx, LoginRequest.class);
        String address = ctx.ip();
        String account = address + "|" + (request.username() == null
                ? "" : request.username().trim().toLowerCase(Locale.ROOT));
        if (accountLoginFailures.isLimited(account) || addressLoginFailures.isLimited(address)) {
            send(ctx, OperationResult.error(ErrorKind.BUSINESS_RULE, TOO_MANY_LOGINS), ApiStatus.OK);
            return;
        }
        char[] password = request.password() == null ? null : request.password().toCharArray();
        OperationResult<?> result = sessions.login(request.username(), password);
        if (result.success()) {
            accountLoginFailures.reset(account);
        } else if (result.error() == ErrorKind.AUTHENTICATION) {
            accountLoginFailures.record(account);
            addressLoginFailures.record(address);
        }
        send(ctx, result, ApiStatus.OK);
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

    /** POST /api/citizens: limited per network address, then the controller (docs/api.md 16.5). */
    private void registerCitizen(Context ctx) {
        if (!registrationLimiter.tryAcquire(ctx.ip())) {
            send(ctx, OperationResult.error(ErrorKind.BUSINESS_RULE, TOO_MANY_REGISTRATIONS), ApiStatus.OK);
            return;
        }
        send(ctx, citizens.register(readBody(ctx, CitizenRequest.class)), ApiStatus.CREATED);
    }

    /** POST /api/panic: limited per network address, then the controller (docs/api.md 16.1). */
    private void reportPanic(Context ctx) {
        if (!panicLimiter.tryAcquire(ctx.ip())) {
            send(ctx, OperationResult.error(ErrorKind.BUSINESS_RULE, TOO_MANY_PANIC), ApiStatus.OK);
            return;
        }
        var result = panic.report(readBody(ctx, PanicAlertRequest.class));
        if (result.success() && gpsHub != null && result.value().createdEmergency()) {
            Long vehicleId = result.value().dispatchedVehicleId();
            if (vehicleId != null) gpsHub.broadcastMission(vehicleId);
            gpsHub.broadcastPanic();
        }
        send(ctx, result, ApiStatus.CREATED);
    }

    /** The filters of GET /api/audit: from, to, module, userId, result and limit, all optional. */
    private static AuditQuery readAuditQuery(Context ctx) {
        return new AuditQuery(
                dateTimeParam(ctx, "from", false),
                dateTimeParam(ctx, "to", true),
                blankToNull(ctx.queryParam("module")),
                longParam(ctx, "userId"),
                resultParam(ctx),
                intParam(ctx, "limit"));
    }

    /** "2026-11-14": a whole day, for the reports. */
    private static boolean hasPeriod(Context ctx) {
        return blankToNull(ctx.queryParam("from")) != null || blankToNull(ctx.queryParam("to")) != null;
    }

    private static LocalDate dateParam(Context ctx, String name) {
        String value = blankToNull(ctx.queryParam(name));
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(BAD_DAY);
        }
    }

    /**
     * "2026-11-14T08:30" or "2026-11-14". A day alone means its first moment, or its last moment
     * when {@code endOfDay} (so ?from=2026-11-14&to=2026-11-14 is the whole day).
     */
    private static LocalDateTime dateTimeParam(Context ctx, String name, boolean endOfDay) {
        String value = blankToNull(ctx.queryParam(name));
        if (value == null) {
            return null;
        }
        try {
            if (value.length() == 10) {
                LocalDate day = LocalDate.parse(value);
                return endOfDay ? day.atTime(LocalTime.MAX) : day.atStartOfDay();
            }
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(BAD_DATE);
        }
    }

    private static Long longParam(Context ctx, String name) {
        String value = blankToNull(ctx.queryParam(name));
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(BAD_ID);
        }
    }

    private static Integer intParam(Context ctx, String name) {
        String value = blankToNull(ctx.queryParam(name));
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(BAD_LIMIT);
        }
    }

    private static AuditResult resultParam(Context ctx) {
        String value = blankToNull(ctx.queryParam("result"));
        try {
            return value == null ? null : AuditResult.fromCode(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(BAD_RESULT);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** ?status=PENDIENTE (or another code of PanicReportStatus); null for all. */
    private static PanicReportStatus readPanicFilter(Context ctx) {
        String code = ctx.queryParam("status");
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return PanicReportStatus.fromCode(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(BAD_PANIC_FILTER);
        }
    }

    /** The body of 16.3 must be {"status": "DESCARTADO"}. */
    private void readDiscard(Context ctx) {
        PanicStatusRequest request = readBody(ctx, PanicStatusRequest.class);
        if (request.status() != PanicReportStatus.DISCARDED) {
            throw new ValidationException(BAD_PANIC_STATUS);
        }
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

    /** Answers with the simulation, or 503 when this server runs without it. */
    private void sendSimulation(Context ctx, Function<SimulationController, OperationResult<?>> work) {
        SimulationController current;
        synchronized (this) {
            current = simulation;
        }
        send(ctx, current == null
                ? OperationResult.error(ErrorKind.UNAVAILABLE, NO_SIMULATION)
                : work.apply(current), ApiStatus.OK);
    }

    /** The body of 10.3; without the segment or the level, a 400. */
    private TrafficRequest readTraffic(Context ctx) {
        TrafficRequest request = readBody(ctx, TrafficRequest.class);
        if (request.segmentId() == null || request.segmentId() <= 0 || request.level() == null) {
            throw new ValidationException(BAD_TRAFFIC);
        }
        return request;
    }

    /** ?level=MODERADO or CONGESTIONADO of 8.2; without it, null (both levels). */
    private static TrafficLevel levelParam(Context ctx) {
        String value = blankToNull(ctx.queryParam("level"));
        try {
            return value == null ? null : TrafficLevel.fromCode(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(BAD_LEVEL);
        }
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

    /** A latitude or longitude of the query (9.4); missing, not a number or out of range: 400. */
    private static double coordinate(Context ctx, String name, double limit) {
        String text = ctx.queryParam(name);
        try {
            double value = Double.parseDouble(text == null ? "" : text.trim());
            if (Double.isFinite(value) && Math.abs(value) <= limit) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the 400 below
        }
        throw new ValidationException(BAD_COORDINATE);
    }

    /**
     * PATCH /api/dispatches/{id}/status (6.6). TRASLADANDO goes with the hospital and starts the
     * transfer (CU-11): the routes to the hospital are saved and the web reads them with 6.4.
     * Every other status goes without a hospital. The answer is always the assignment.
     */
    private record DriverTransferRequest(Long hospitalId) { }

    /** Sends an operation result and wakes WebSocket maps as soon as its mission changed. */
    private void sendDriverMission(Context ctx, OperationResult<DriverMission> result) {
        if (result.success()) broadcastMission(result.value().unit().vehicleId());
        send(ctx, result, ApiStatus.OK);
    }

    private void broadcastMission(long vehicleId) {
        if (gpsHub != null) gpsHub.broadcastMission(vehicleId);
    }

    private OperationResult<EmergencyAssignment> advanceMission(long assignmentId, AssignmentStatusRequest request) {
        AssignmentStatus status = request.status();
        if (status == null) {
            throw new ValidationException(BAD_MISSION_STATUS);
        }
        Long hospitalId = request.hospitalId();
        if (status != AssignmentStatus.TRANSPORTING) {
            if (hospitalId != null) {
                throw new ValidationException(HOSPITAL_WITHOUT_TRANSFER);
            }
            return dispatch.advance(assignmentId, status);
        }
        if (hospitalId == null || hospitalId <= 0) {
            throw new ValidationException(TRANSFER_WITHOUT_HOSPITAL);
        }
        OperationResult<DispatchResult> transfer = dispatch.startTransfer(assignmentId, hospitalId);
        return transfer.success()
                ? OperationResult.ok(transfer.value().assignment())
                : OperationResult.error(transfer.error(), transfer.message());
    }

    /** The new status of a user, station or hospital: A or I; without it, a 400. */
    private RecordStatus readRecordStatus(Context ctx) {
        RecordStatus status = readBody(ctx, RecordStatusRequest.class).status();
        if (status == null) {
            throw new ValidationException(BAD_RECORD_STATUS);
        }
        return status;
    }

    /** The new operational status of PATCH /api/units/{id}/status; without it, a 400. */
    private VehicleOperationalStatus readUnitStatus(Context ctx) {
        VehicleOperationalStatus status = readBody(ctx, VehicleStatusRequest.class).status();
        if (status == null) {
            throw new ValidationException(BAD_UNIT_STATUS);
        }
        return status;
    }

    /** POST /api/auth/password: the user of the session changes his own password. */
    private void changePassword(Context ctx) {
        PasswordChangeRequest request = readBody(ctx, PasswordChangeRequest.class);
        if (request.currentPassword() == null || request.newPassword() == null) {
            throw new ValidationException(BAD_PASSWORD);
        }
        send(ctx, admin.users().changeOwnPassword(request.currentPassword().toCharArray(),
                request.newPassword().toCharArray()), ApiStatus.OK);
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
        // The answers carry tokens and personal data: never cached, never read as another type.
        ctx.header("Cache-Control", "no-store");
        ctx.header("X-Content-Type-Options", "nosniff");
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
