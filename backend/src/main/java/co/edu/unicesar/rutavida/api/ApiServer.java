package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.AlertController;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.PanicController;
import co.edu.unicesar.rutavida.controller.ReportController;
import co.edu.unicesar.rutavida.controller.TrackingController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.dto.AuditQuery;
import co.edu.unicesar.rutavida.dto.EmergencyRequest;
import co.edu.unicesar.rutavida.dto.HospitalRequest;
import co.edu.unicesar.rutavida.dto.ErrorKind;
import co.edu.unicesar.rutavida.dto.IncidentRequest;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.dto.PanicAlertRequest;
import co.edu.unicesar.rutavida.dto.StationRequest;
import co.edu.unicesar.rutavida.dto.UserRequest;
import co.edu.unicesar.rutavida.dto.VehicleRequest;
import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.RoadIncident;
import co.edu.unicesar.rutavida.model.UserSession;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.PanicReportStatus;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
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
 * (A12c), alerts (A12f), dispatch and routes (A12d), closing an emergency (A16),
 * administration of users, units, stations and hospitals (A17), audit (A18), tracking
 * (A12e), panic button (A20b) and the operations report (A23). The simulation is added when
 * its controller exists (K9).
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
    private static final String BAD_RECORD_STATUS = "El estado debe ser A (activo) o I (inactivo).";
    private static final String BAD_UNIT_STATUS = "El estado de la unidad no es válido.";
    private static final String BAD_PASSWORD = "Escriba la contraseña actual y la nueva.";
    private static final String BAD_DATE = "La fecha debe tener la forma 2026-11-14 o 2026-11-14T08:30.";
    private static final String BAD_DAY = "La fecha debe tener la forma 2026-11-14.";
    private static final String BAD_LIMIT = "El límite debe ser un número entero.";
    private static final String BAD_RESULT = "El resultado debe ser EXITO o FALLO.";
    private static final String BAD_PANIC_STATUS = "Una alerta de pánico solo se puede marcar DESCARTADO.";
    private static final String BAD_PANIC_FILTER = "El estado del reporte de pánico no es válido.";
    private static final String TOO_MANY_PANIC =
            "Se enviaron demasiadas alertas desde esta conexión. Si la emergencia sigue, llame al 123.";

    /** Panic button: reports per network address in 10 minutes (RN-13 limits each device to 3). */
    static final int PANIC_PER_ADDRESS = 20;
    static final Duration PANIC_WINDOW = Duration.ofMinutes(10);

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
    private final RequestRateLimiter panicLimiter;
    private final Path frontendDirectory;
    private final ObjectMapper json = JsonMapperFactory.create();
    private Javalin app;

    /**
     * @param frontendDirectory folder with index.html; null to serve only the API (tests)
     */
    public ApiServer(WebSessionController sessions, CatalogController catalogs,
                     EmergencyController emergencies, DashboardController dashboard, ReportController reports,
                     IncidentController incidents, RoadMapController roadMap, AlertController alerts,
                     DispatchController dispatch, AdminControllers admin, TrackingController tracking,
                     PanicController panic, Path frontendDirectory) {
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
        this.panicLimiter = new RequestRateLimiter(PANIC_PER_ADDRESS, PANIC_WINDOW, Clock.systemUTC());
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

        // 8. Live map (A12e). The map asks every 5 seconds by itself (DT-07), so it does not keep
        // the session alive (DT-08).
        config.routes.get("/api/tracking", secured("EMERGENCIAS", "CONSULTAR", AUTOMATIC,
                ctx -> send(ctx, tracking.snapshot(), ApiStatus.OK)));

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
        config.routes.post("/api/panic", optionalSession(this::reportPanic));
        // The types for the button page (A20c): public like POST /api/panic, so a citizen without an
        // account can say it is a fire and the alert sends firefighters. Read only, no personal data.
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
    /**
     * Wraps a public handler: with a valid token it runs with that session (a police officer);
     * without a token, or with one that expired, it runs without session. In an emergency the
     * button must never answer "log in first".
     */
    Handler optionalSession(Handler handler) {
        return ctx -> {
            String header = ctx.header(AUTHORIZATION);
            if (header != null && !header.isBlank()) {
                OperationResult<UserSession> session = sessions.authenticate(header, USER_ACTION);
                if (session.success()) {
                    sessions.runAs(session.value(), () -> handler.handle(ctx));
                    return;
                }
            }
            handler.handle(ctx);
        };
    }

    /** POST /api/panic: limited per network address, then the controller (docs/api.md 16.1). */
    private void reportPanic(Context ctx) {
        if (!panicLimiter.tryAcquire(ctx.ip())) {
            send(ctx, OperationResult.error(ErrorKind.BUSINESS_RULE, TOO_MANY_PANIC), ApiStatus.OK);
            return;
        }
        send(ctx, panic.report(readBody(ctx, PanicAlertRequest.class)), ApiStatus.CREATED);
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