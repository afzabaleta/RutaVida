package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.AuditController;
import co.edu.unicesar.rutavida.controller.AlertController;
import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.CatalogTestSupport;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.controller.ReportController;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.FleetController;
import co.edu.unicesar.rutavida.controller.DriverOperationController;
import co.edu.unicesar.rutavida.controller.GpsController;
import co.edu.unicesar.rutavida.controller.TrackingController;
import co.edu.unicesar.rutavida.controller.HospitalController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.SessionContext;
import co.edu.unicesar.rutavida.controller.UserController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.dto.DashboardSummary;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import co.edu.unicesar.rutavida.service.AuditQueryService;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.DashboardService;
import co.edu.unicesar.rutavida.service.ReportService;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.FleetService;
import co.edu.unicesar.rutavida.service.DriverOperationService;
import co.edu.unicesar.rutavida.service.HospitalService;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyStationDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyVehicleDAO;
import co.edu.unicesar.rutavida.service.InMemoryHospitalDAO;
import co.edu.unicesar.rutavida.service.IncidentTestSupport;
import co.edu.unicesar.rutavida.service.PanicTestSupport;
import co.edu.unicesar.rutavida.service.RoadMapService;
import co.edu.unicesar.rutavida.service.UserService;
import co.edu.unicesar.rutavida.service.EmergencyService;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyTypeDAO;
import co.edu.unicesar.rutavida.util.PasswordHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panic button through real HTTP (A20b and DT-13, docs/api.md 16), on the small city of
 * PanicTestSupport with EXAMPLE data:
 * <ul>
 *   <li>"ciudadano1", "ciudadano2"...: CIUDADANO, created when a test asks for them;</li>
 *   <li>"policia.web": POLICIA, validates the alert alone and sees the reports;</li>
 *   <li>"coordinador.web": COORDINADOR, sees and discards the reports;</li>
 *   <li>"sinpermisos.web": a role without permissions.</li>
 * </ul>
 */
class PanicApiTest {

    private static final String PASSWORD = "Clave-Segura1";
    private static final Clock CLOCK = EmergencyTestSupport.CLOCK;

    /** A corner of the small city about 490 m from the scene: inside the road network, outside the 300 m. */
    private static final GeoPoint FAR = DispatchTestSupport.at(0, 0);

    private final ObjectMapper json = JsonMapperFactory.create();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private PanicTestSupport panic;
    private ApiServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        panic = new PanicTestSupport();
        panic.roles.add(new Role(8, "SIN_PERMISOS", "Prueba", RecordStatus.ACTIVE, Set.of()));
        PasswordHasher hasher = new PasswordHasher(1_000);
        String hash = hasher.hash(PASSWORD.toCharArray());
        LocalDateTime registered = LocalDateTime.now(CLOCK);
        panic.users.insert(new User(0, 6, "PRUEBA-P1", "Pablo", "Policía", "policia.web@ejemplo.com",
                "policia.web", hash, RecordStatus.ACTIVE, registered));
        panic.users.insert(new User(0, 3, "PRUEBA-C1", "Carla", "Coordinadora", "coordinador.web@ejemplo.com",
                "coordinador.web", hash, RecordStatus.ACTIVE, registered));
        panic.users.insert(new User(0, 8, "PRUEBA-S1", "Sara", "Sin permisos", "sinpermisos.web@ejemplo.com",
                "sinpermisos.web", hash, RecordStatus.ACTIVE, registered));

        AuditService audit = new AuditService(new InMemoryAuditDAO(), CLOCK);
        EmergencyTestSupport.CountingTransactions transactions = new EmergencyTestSupport.CountingTransactions();
        AuthService auth = new AuthService(panic.users, panic.roles, hasher, audit, CLOCK);
        SessionContext session = new SessionContext();
        AlertService alertService = new AlertService(panic.alerts, panic.users, panic.roles, transactions, CLOCK);

        // The other routes are not tested here; they only need their controllers.
        AdminControllers admin = new AdminControllers(
                new UserController(new UserService(panic.users, panic.roles, hasher, audit, transactions, CLOCK),
                        auth, session),
                new FleetController(new FleetService(new InMemoryEmergencyVehicleDAO(),
                        new InMemoryEmergencyStationDAO(), List::of, alertService, audit, transactions), auth, session),
                new HospitalController(new HospitalService(new InMemoryHospitalDAO(), audit, transactions),
                        auth, session),
                new AuditController(new AuditQueryService(new InMemoryAuditDAO(), panic.users, CLOCK), auth, session));
        EmergencyController emergencies = new EmergencyController(new EmergencyService(new InMemoryEmergencyDAO(),
                new InMemoryEmergencyTypeDAO(), new EmergencyTestSupport.FixedAssignments(),
                new EmergencyTestSupport.FixedRoutes(), audit, transactions, CLOCK), auth, session);
        DashboardController dashboard = new DashboardController(new DashboardService(
                (since, start, end) -> new DashboardSummary(0, 0, 0, 0, 0, 0, 0, null), CLOCK));
        IncidentController incidents = new IncidentController(new IncidentTestSupport().service(), auth, session);
        var graph = panic.city.graph;
        RoadMapController roadMap = new RoadMapController(new RoadMapService(ids ->
                IntStream.range(0, graph.edgeCount()).mapToObj(graph::edge)
                        .filter(edge -> ids.contains(edge.dbId()))
                        .map(edge -> new SegmentView(edge.dbId(), "Prueba",
                                pointAt(edge.from()), pointAt(edge.to()))).toList()));
        AlertController alerts = new AlertController(alertService, session);
        DispatchController dispatch = new DispatchController(panic.city.service(), auth, session);

        // The operations report (A23) has its own test in ApiServerTest; here it only has to exist.
        ReportController reports = new ReportController(new ReportService((first, last) -> {
            throw new UnsupportedOperationException("not used here");
        }, CLOCK));

        var web = new WebSessionController(auth, session, CLOCK, panic.citizenService());
        var gpsController = new GpsController(panic.gps, auth, session);
        var driver = new DriverOperationController(new DriverOperationService(panic.links,
                panic.city.vehicles, panic.city.assignments, panic.city.emergencies, panic.city.service(),
                alertService, audit, panic.city.transactions, CLOCK, panic.city.emergencyTypes,
                panic.liveMap(), panic.gps), auth, session);
        server = new ApiServer(web,
                new CatalogController(new CatalogTestSupport().service()), emergencies, dashboard, reports, incidents,
                roadMap, alerts, dispatch, admin, new TrackingController(panic.liveMap(), auth, session),
                panic.controller(auth, session), panic.citizenController(), null)
                .withGps(gpsController, new GpsHub(web, gpsController::assignedVehicleId))
                .withDriverOperations(driver);
        int port = freePort();
        server.start(port);
        base = "http://localhost:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void theButtonPageReadsTheEmergencyTypesWithoutAccount() throws Exception {
        HttpResponse<String> response = send("GET", "/api/panic/emergency-types", null, null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode types = value(response);
        assertEquals(CatalogTestSupport.EMERGENCY_TYPES.size(), types.size());
        assertEquals("EMERGENCIA_MEDICA", types.get(0).get("name").asText());
        assertEquals(1, types.get(0).get("id").asLong());
        assertEquals(401, send("GET", "/api/emergency-types", null, null).statusCode(),
                "the full catalog route still needs a session");
    }

    // ------------------------------------------------------------------ CU-23: citizen accounts

    @Test
    void aPersonRegistersLogsInOnceAndThePhoneStaysLoggedIn30Days() throws Exception {
        HttpResponse<String> created = send("POST", "/api/citizens", null, registration("laura.web", true));
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(json.readTree(created.body()).get("message").asText().contains("30 días"));
        assertFalse(created.body().contains("Clave-Segura1"), "the password never comes back");
        assertFalse(created.body().contains("passwordHash"));

        HttpResponse<String> login = send("POST", "/api/auth/login", null,
                "{\"username\": \"laura.web\", \"password\": \"" + PASSWORD + "\"}");
        assertEquals(200, login.statusCode(), login.body());
        JsonNode session = value(login);
        assertEquals("CIUDADANO", session.get("role").asText());
        assertEquals(0, session.get("idleTimeoutMinutes").asInt(), "no idle timeout on the button");
        assertEquals(LocalDateTime.now(CLOCK).plusDays(30).withNano(0),
                LocalDateTime.parse(session.get("expiresAt").asText()));
        assertEquals("[\"PANICO:REPORTAR\"]", session.get("permissions").toString());

        String token = session.get("token").asText();
        assertEquals(200, send("GET", "/api/auth/session", token, null).statusCode());
        assertEquals(403, send("GET", "/api/panic/reports", token, null).statusCode(),
                "the citizen token only serves to press the button");
    }

    @Test
    void withoutAuthorizationOrWithTakenDataThereIsNoAccountAndAddressesAreLimited() throws Exception {
        assertEquals(400, send("POST", "/api/citizens", null, registration("sin.permiso", false)).statusCode());
        assertEquals(201, send("POST", "/api/citizens", null, registration("laura.web", true)).statusCode());
        assertEquals(409, send("POST", "/api/citizens", null, registration("laura.web", true)).statusCode());

        for (int i = 0; i < ApiServer.REGISTRATIONS_PER_ADDRESS; i++) {
            send("POST", "/api/citizens", null, registration("persona" + i + ".web", true));
        }
        HttpResponse<String> flood = send("POST", "/api/citizens", null, registration("una.mas", true));
        assertEquals(409, flood.statusCode(), flood.body());
        assertTrue(json.readTree(flood.body()).get("message").asText().contains("demasiadas cuentas"));
    }

    // ------------------------------------------------------------------ CU-22: the button with an account

    @Test
    void withoutAnAccountTheButtonSays401() throws Exception {
        assertEquals(401, send("POST", "/api/panic", null, press("celular-1", 0)).statusCode());
        assertEquals(401, send("POST", "/api/panic", "token-vencido", press("celular-1", 0)).statusCode());
        assertEquals(403, send("POST", "/api/panic", login("coordinador.web"), press("celular-1", 0)).statusCode(),
                "the coordinator does not press the button");
    }

    @Test
    void aCitizenValidatesWithOneReportAndSeesWhenNoGpsUnitIsAvailable() throws Exception {
        HttpResponse<String> response = send("POST", "/api/panic", citizen(1), press("celular-1", 0));

        assertEquals(201, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertTrue(body.get("success").asBoolean());
        assertTrue(body.get("message").asText().contains("GPS activo"));
        assertEquals("VALIDADO", body.get("value").get("status").asText());
        assertFalse(body.get("value").get("emergencyId").isNull());
        assertEquals(1, body.get("value").get("reportsNeeded").asInt());
    }

    @Test
    void repeatedCitizensReportsLinkToTheFirstEmergency() throws Exception {
        var first = value(send("POST", "/api/panic", citizen(1), press("celular-1", 0)));
        send("POST", "/api/panic", citizen(2), press("celular-2", 60));
        HttpResponse<String> third = send("POST", "/api/panic", citizen(3), press("celular-3", -40));

        assertEquals(201, third.statusCode(), third.body());
        JsonNode body = json.readTree(third.body());
        assertTrue(body.get("message").asText().contains("vinculó"));
        assertEquals("VALIDADO", body.get("value").get("status").asText());
        assertTrue(body.get("value").get("dispatchedUnit").isNull());
        assertEquals(first.get("emergencyId"), body.get("value").get("emergencyId"));
    }

    @Test
    void aPoliceOfficerWithSessionValidatesAlone() throws Exception {
        HttpResponse<String> police = send("POST", "/api/panic", login("policia.web"), press("celular-policia", 0));
        assertEquals(201, police.statusCode(), police.body());
        assertEquals("VALIDADO", value(police).get("status").asText());
    }

    @Test
    void wrongReportsAre400AndTheFourthOfAPersonIs409() throws Exception {
        String token = citizen(9);
        assertEquals(400, send("POST", "/api/panic", token, "{\"deviceId\": \"celular-1\"}").statusCode(),
                "no location");
        assertEquals(400, send("POST", "/api/panic", token,
                "{\"deviceId\": \"celular-1\", \"location\": {\"latitude\": 10.60, \"longitude\": -73.00}}")
                .statusCode(), "outside the road network");
        assertEquals(400, send("POST", "/api/panic", token, "{\"deviceId\":").statusCode(), "malformed");

        for (int i = 0; i < 3; i++) {
            assertEquals(201, send("POST", "/api/panic", token, press("celular-" + i, i * 10)).statusCode());
        }
        HttpResponse<String> fourth = send("POST", "/api/panic", token, press("otro-celular", 0));
        assertEquals(409, fourth.statusCode(), "the limit is per person, whatever phone it uses");
        assertTrue(json.readTree(fourth.body()).get("message").asText().contains("123"));
    }

    @Test
    void oneNetworkAddressCannotFloodTheButton() throws Exception {
        for (int i = 0; i < ApiServer.PANIC_PER_ADDRESS; i++) {
            send("POST", "/api/panic", citizen(i / 3 + 1), press("celular-" + i, 0));
        }

        HttpResponse<String> flood = send("POST", "/api/panic", citizen(20), press("celular-nuevo", 0));
        assertEquals(409, flood.statusCode(), flood.body());
        assertTrue(json.readTree(flood.body()).get("message").asText().contains("demasiadas alertas"));
    }

    @Test
    void thePanelListsTheReportsWithoutTheDeviceAndFiltersByStatus() throws Exception {
        send("POST", "/api/panic", citizen(1), press("celular-1", 0));
        send("POST", "/api/panic", login("policia.web"), pressAt("celular-policia", FAR));
        String coordinator = login("coordinador.web");

        HttpResponse<String> all = send("GET", "/api/panic/reports", coordinator, null);
        assertEquals(200, all.statusCode(), all.body());
        assertEquals(2, value(all).size());
        assertFalse(all.body().contains("deviceId"), "the device hash never leaves the server");
        assertTrue(value(all).get(0).get("byPolice").asBoolean(), "newest first: the police report");

        JsonNode pending = value(send("GET", "/api/panic/reports?status=PENDIENTE", coordinator, null));
        assertEquals(0, pending.size());

        assertEquals(400, send("GET", "/api/panic/reports?status=VOLANDO", coordinator, null).statusCode());
        assertEquals(401, send("GET", "/api/panic/reports", null, null).statusCode());
        assertEquals(403, send("GET", "/api/panic/reports", login("sinpermisos.web"), null).statusCode());
    }

    @Test
    void aCoordinatorDiscardsAPendingAlertAndOnlyThat() throws Exception {
        long id = panic.oldReport(1, 1);
        String path = "/api/panic/reports/" + id + "/status";
        String coordinator = login("coordinador.web");

        assertEquals(400, send("PATCH", path, coordinator, "{\"status\": \"VALIDADO\"}").statusCode());
        assertEquals(403, send("PATCH", path, login("policia.web"), "{\"status\": \"DESCARTADO\"}").statusCode(),
                "the police sees the reports but does not discard them");

        HttpResponse<String> discarded = send("PATCH", path, coordinator, "{\"status\": \"DESCARTADO\"}");
        assertEquals(200, discarded.statusCode(), discarded.body());
        assertEquals("DESCARTADO", value(discarded).get("status").asText());
        assertEquals(409, send("PATCH", path, coordinator, "{\"status\": \"DESCARTADO\"}").statusCode(),
                "it is no longer PENDIENTE");
        assertEquals(404, send("PATCH", "/api/panic/reports/999/status", coordinator,
                "{\"status\": \"DESCARTADO\"}").statusCode());
    }

    @Test
    void twoFalseAlarmsBlockTheCitizenAndItsPhoneStopsWorking() throws Exception {
        String phone = citizen(1);
        String coordinator = login("coordinador.web");

        for (int i = 0; i < 2; i++) {
            JsonNode report = value(send("POST", "/api/panic", phone, press("celular-1", 0)));
            long id = report.get("reportId").asLong();
            long emergencyId = report.get("emergencyId").asLong();
            assertEquals(200, send("POST", "/api/emergencies/" + emergencyId + "/close", coordinator, null).statusCode());
            assertEquals(200, send("PATCH", "/api/panic/reports/" + id + "/status", coordinator,
                    "{\"status\": \"DESCARTADO\"}").statusCode());
        }

        assertEquals(401, send("POST", "/api/panic", phone, press("celular-1", 0)).statusCode(),
                "the phone was logged out");
        HttpResponse<String> again = send("POST", "/api/auth/login", null,
                "{\"username\": \"ciudadano1\", \"password\": \"" + PASSWORD + "\"}");
        assertEquals(401, again.statusCode(), "blocked: it cannot log in again");
    }

    @Test
    void panicPushesPrivateMissionAndBothMapsReadTheGpsRouteAndProgress() throws Exception {
        var available = VehicleOperationalStatus.AVAILABLE;
        long ambulance = panic.ambulance("AMB-GPS-11", DispatchTestSupport.at(1, 1), available);
        long other = panic.ambulance("AMB-GPS-22", DispatchTestSupport.at(0, 0), available);
        panic.bindDriver("camila.conductora", ambulance, RecordStatus.ACTIVE);
        panic.bindDriver("tomás.conductor", other, RecordStatus.ACTIVE);
        String driver = login("camila.conductora");
        String unrelated = login("tomás.conductor");
        String coordinator = login("coordinador.web");

        try (var driverSocket = socket(driver, "driver");
             var otherSocket = socket(unrelated, "driver");
             var coordinatorSocket = socket(coordinator, "viewer")) {
            assertEquals(200, gps(driver, DispatchTestSupport.at(1, 1)).statusCode());
            assertEquals("position", coordinatorSocket.next().get("type").asText());
            JsonNode report = value(send("POST", "/api/panic", citizen(1), press("panico-web", 0)));
            long emergency = report.get("emergencyId").asLong();
            assertEquals(ambulance, report.get("dispatchedVehicleId").asLong());
            assertEquals("mission", driverSocket.next().get("type").asText());
            assertEquals("mission", coordinatorSocket.next().get("type").asText());
            assertEquals("panic", coordinatorSocket.next().get("type").asText());
            assertNull(otherSocket.events.poll(250, TimeUnit.MILLISECONDS), "unrelated driver receives no mission");

            JsonNode ownMission = value(send("GET", "/api/gps/mission", driver, null));
            assertEquals(emergency, ownMission.get("emergencyId").asLong());
            assertEquals("EMERGENCIA_MEDICA", ownMission.get("emergencyType").asText());
            assertTrue(ownMission.get("tracking").get("etaMinutes").isNumber());
            long original = ownMission.get("tracking").get("activeRouteId").asLong();
            long assignment = ownMission.get("assignmentId").asLong();
            JsonNode ownRoute = value(send("GET", "/api/gps/mission/routes", driver, null)).get(0);
            assertEquals(original, ownRoute.get("routeId").asLong());
            assertTrue(ownRoute.get("path").size() > 1);
            assertEquals(0, value(send("GET", "/api/gps/mission/routes", unrelated, null)).size());
            assertTrue(value(send("GET", "/api/gps/mission", unrelated, null)).get("assignmentId").isNull());
            JsonNode staffRoute = value(send("GET", "/api/dispatches/" + assignment + "/routes", coordinator, null));
            assertEquals(ownRoute.get("path"), staffRoute.get(0).get("path"));

            assertEquals(200, gps(driver, new GeoPoint(10.60, -73.00)).statusCode());
            assertEquals("position", coordinatorSocket.next().get("type").asText());
            JsonNode unreachable = value(send("GET", "/api/gps/mission", driver, null)).get("tracking");
            assertTrue(unreachable.get("routeUnavailable").asBoolean());
            assertEquals(original, unreachable.get("activeRouteId").asLong());
            assertTrue(value(send("GET", "/api/tracking", coordinator, null))
                    .get("missions").get(0).get("routeUnavailable").asBoolean());

            panic.gpsClock.advance(Duration.ofSeconds(21));
            GeoPoint deviation = DispatchTestSupport.at(0, 1);
            assertTrue(value(gps(driver, deviation)).get("routeRecalculated").asBoolean());
            JsonNode moving = coordinatorSocket.next().get("position");
            assertEquals(ambulance, moving.get("vehicleId").asLong());
            assertEquals(deviation.latitude(), moving.get("location").get("latitude").asDouble(), 0.000001);
            JsonNode revised = value(send("GET", "/api/gps/mission", driver, null)).get("tracking");
            assertTrue(revised.get("activeRouteId").asLong() != original);
            JsonNode staffMission = value(send("GET", "/api/tracking", coordinator, null)).get("missions").get(0);
            assertEquals(revised.get("activeRouteId"), staffMission.get("activeRouteId"));
            assertEquals(revised.get("destination"), staffMission.get("destination"));

            assertEquals(409, send("POST", "/api/gps/session/stop", driver, null).statusCode());
            assertEquals("EN_SITIO", value(send("POST", "/api/gps/mission/arrival", driver, null)).get("status").asText());
            assertEquals("mission", driverSocket.next().get("type").asText());
            assertEquals("mission", coordinatorSocket.next().get("type").asText());

            String transfer = "/api/gps/mission/" + assignment;
            long hospital = DispatchTestSupport.CONFIRMED_HOSPITAL;
            String transferBody = "{\"hospitalId\":" + hospital + "}";
            assertEquals(403, send("GET", transfer + "/hospitals", coordinator, null).statusCode());
            assertEquals(409, send("POST", transfer + "/transfer", unrelated, transferBody).statusCode());
            JsonNode hospitals = value(send("GET", transfer + "/hospitals", driver, null));
            assertTrue(hospitals.size() > 0);
            assertEquals(hospital, hospitals.get(0).get("hospitalId").asLong());
            assertTrue(hospitals.get(0).get("etaMinutes").isNumber());
            assertTrue(hospitals.get(0).get("distanceKm").isNumber());
            var started = send("POST", transfer + "/transfer", driver, transferBody);
            assertEquals(200, started.statusCode(), started.body());
            JsonNode toHospital = value(started);
            assertEquals("TRASLADANDO", toHospital.get("status").asText());
            assertEquals("mission", driverSocket.next().get("type").asText());
            assertEquals("mission", coordinatorSocket.next().get("type").asText());
            long firstHospitalRoute = toHospital.get("tracking").get("activeRouteId").asLong();

            panic.gpsClock.advance(Duration.ofSeconds(3));
            assertTrue(value(gps(driver, DispatchTestSupport.at(1, 1))).get("routeRecalculated").asBoolean());
            assertEquals("position", coordinatorSocket.next().get("type").asText());
            JsonNode hospitalRoute = value(send("GET", "/api/gps/mission/routes", driver, null)).get(0);
            assertTrue(hospitalRoute.get("routeId").asLong() != firstHospitalRoute);
            assertEquals(hospital, hospitalRoute.get("destinationHospitalId").asLong());
            assertEquals(hospitalRoute.get("routeId"), value(send("GET", "/api/tracking", coordinator, null))
                    .get("missions").get(0).get("activeRouteId"));

            JsonNode finished = value(send("POST", "/api/gps/mission/finish", driver, null));
            assertEquals("FINALIZADA", finished.get("status").asText());
            assertTrue(finished.get("finishedAt").isTextual());
            assertEquals("mission", driverSocket.next().get("type").asText());
            assertEquals("mission", coordinatorSocket.next().get("type").asText());
            assertEquals(0, value(send("GET", "/api/tracking", coordinator, null)).get("missions").size());
            panic.gpsClock.advance(Duration.ofSeconds(3));
            assertEquals(200, gps(driver, DispatchTestSupport.at(0, 0)).statusCode());
            assertEquals("position", coordinatorSocket.next().get("type").asText());
            assertEquals(200, send("POST", "/api/gps/session/stop", driver, null).statusCode());
        }
    }

    private GeoPoint pointAt(int index) {
        var node = panic.city.graph.node(index);
        return new GeoPoint(node.lat(), node.lon());
    }

    private HttpResponse<String> gps(String token, GeoPoint point) throws Exception {
        return send("POST", "/api/gps/position", token,
                json.writeValueAsString(java.util.Map.of("location", point)));
    }

    private SocketEvents socket(String token, String audience) throws Exception {
        SocketEvents events = new SocketEvents();
        events.socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(base.replace("http:", "ws:") + "/ws/gps"), events)
                .get(5, TimeUnit.SECONDS);
        events.socket.sendText(json.writeValueAsString(java.util.Map.of(
                "type", "auth", "token", token, "audience", audience)), true).get(5, TimeUnit.SECONDS);
        assertEquals("ready", events.next().get("type").asText());
        return events;
    }

    private final class SocketEvents implements WebSocket.Listener, AutoCloseable {
        final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        final StringBuilder frame = new StringBuilder();
        WebSocket socket;

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket source, CharSequence data, boolean last) {
            frame.append(data);
            if (last) {
                events.add(frame.toString());
                frame.setLength(0);
            }
            source.request(1);
            return null;
        }

        JsonNode next() throws Exception {
            String event = events.poll(5, TimeUnit.SECONDS);
            if (event == null) throw new AssertionError("WebSocket event did not arrive");
            return json.readTree(event);
        }

        @Override public void close() { socket.abort(); }
    }

    // ------------------------------------------------------------------ helpers

    /** Body of a press of the button {@code metersNorth} away from the scene of PanicTestSupport. */
    private static String press(String device, double metersNorth) {
        return pressAt(device, PanicTestSupport.north(metersNorth));
    }

    /** Body of a press of the button at {@code point}. */
    private static String pressAt(String device, GeoPoint point) {
        return String.format(Locale.ROOT,
                "{\"deviceId\": \"%s\", \"location\": {\"latitude\": %.6f, \"longitude\": %.6f}}",
                device, point.latitude(), point.longitude());
    }

    /** Body of a citizen registration (CU-23) with the password of these tests. */
    private static String registration(String username, boolean consent) {
        return "{\"document\": \"" + String.format("%010d", Math.abs(username.hashCode() % 1_000_000_000))
                + "\", \"firstNames\": \"Laura\","
                + " \"lastNames\": \"Prueba\", \"email\": \"" + username + "@ejemplo.com\","
                + " \"username\": \"" + username + "\", \"password\": \"" + PASSWORD + "\","
                + " \"dataConsent\": " + consent + "}";
    }

    /** Logs in citizen number {@code n} of PanicTestSupport (created the first time) and returns its token. */
    private String citizen(int n) throws Exception {
        return login(panic.citizen(n).user().getUsername());
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/login", null,
                "{\"username\": \"" + username + "\", \"password\": \"" + PASSWORD + "\"}");
        assertEquals(200, response.statusCode(), response.body());
        return value(response).get("token").asText();
    }

    private JsonNode value(HttpResponse<String> response) throws IOException {
        return json.readTree(response.body()).get("value");
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
