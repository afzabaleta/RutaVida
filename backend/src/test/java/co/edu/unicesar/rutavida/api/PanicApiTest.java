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
import co.edu.unicesar.rutavida.controller.HospitalController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.SessionContext;
import co.edu.unicesar.rutavida.controller.UserController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.dto.DashboardSummary;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.service.AuditQueryService;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.DashboardService;
import co.edu.unicesar.rutavida.service.ReportService;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.FleetService;
import co.edu.unicesar.rutavida.service.HospitalService;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyStationDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyVehicleDAO;
import co.edu.unicesar.rutavida.service.InMemoryHospitalDAO;
import co.edu.unicesar.rutavida.service.IncidentTestSupport;
import co.edu.unicesar.rutavida.service.PanicTestSupport;
import co.edu.unicesar.rutavida.service.RoadMapService;
import co.edu.unicesar.rutavida.service.TrackingTestSupport;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        RoadMapController roadMap = new RoadMapController(new RoadMapService(ids -> List.of()));
        AlertController alerts = new AlertController(alertService, session);
        DispatchController dispatch = new DispatchController(panic.city.service(), auth, session);

        // The operations report (A23) has its own test in ApiServerTest; here it only has to exist.
        ReportController reports = new ReportController(new ReportService((first, last) -> {
            throw new UnsupportedOperationException("not used here");
        }, CLOCK));

        server = new ApiServer(new WebSessionController(auth, session, CLOCK, panic.citizenService()),
                new CatalogController(new CatalogTestSupport().service()), emergencies, dashboard, reports, incidents,
                roadMap, alerts, dispatch, admin, new TrackingTestSupport().controller(auth, session),
                panic.controller(auth, session), panic.citizenController(), null);
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
    void aCitizenPressesTheButtonAndIsToldToWait() throws Exception {
        HttpResponse<String> response = send("POST", "/api/panic", citizen(1), press("celular-1", 0));

        assertEquals(201, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertTrue(body.get("success").asBoolean());
        assertEquals("Su reporte quedó registrado. Esperamos la confirmación de otras personas cercanas.",
                body.get("message").asText());
        assertEquals("PENDIENTE", body.get("value").get("status").asText());
        assertTrue(body.get("value").get("emergencyId").isNull());
        assertEquals(3, body.get("value").get("reportsNeeded").asInt());
    }

    @Test
    void threeCitizensCloseTogetherValidateTheAlertAndAUnitIsSent() throws Exception {
        send("POST", "/api/panic", citizen(1), press("celular-1", 0));
        send("POST", "/api/panic", citizen(2), press("celular-2", 60));
        HttpResponse<String> third = send("POST", "/api/panic", citizen(3), press("celular-3", -40));

        assertEquals(201, third.statusCode(), third.body());
        JsonNode body = json.readTree(third.body());
        assertEquals("Su alerta fue validada: la ayuda va en camino.", body.get("message").asText());
        assertEquals("VALIDADO", body.get("value").get("status").asText());
        assertEquals("EJEMPLO-AMB-01", body.get("value").get("dispatchedUnit").asText());
        assertFalse(body.get("value").get("emergencyId").isNull());
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
        assertEquals(1, pending.size());
        assertEquals("PENDIENTE", pending.get(0).get("status").asText());

        assertEquals(400, send("GET", "/api/panic/reports?status=VOLANDO", coordinator, null).statusCode());
        assertEquals(401, send("GET", "/api/panic/reports", null, null).statusCode());
        assertEquals(403, send("GET", "/api/panic/reports", login("sinpermisos.web"), null).statusCode());
    }

    @Test
    void aCoordinatorDiscardsAPendingAlertAndOnlyThat() throws Exception {
        long id = value(send("POST", "/api/panic", citizen(1), press("celular-1", 0))).get("reportId").asLong();
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
            long id = value(send("POST", "/api/panic", phone, press("celular-1", 0))).get("reportId").asLong();
            assertEquals(200, send("PATCH", "/api/panic/reports/" + id + "/status", coordinator,
                    "{\"status\": \"DESCARTADO\"}").statusCode());
        }

        assertEquals(401, send("POST", "/api/panic", phone, press("celular-1", 0)).statusCode(),
                "the phone was logged out");
        HttpResponse<String> again = send("POST", "/api/auth/login", null,
                "{\"username\": \"ciudadano1\", \"password\": \"" + PASSWORD + "\"}");
        assertEquals(401, again.statusCode(), "blocked: it cannot log in again");
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
        return "{\"document\": \"DOC" + Math.abs(username.hashCode()) + "\", \"firstNames\": \"Laura\","
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
