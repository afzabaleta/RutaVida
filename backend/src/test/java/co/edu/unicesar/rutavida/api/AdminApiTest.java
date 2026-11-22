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
import co.edu.unicesar.rutavida.model.EmergencyStation;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.model.Permission;
import co.edu.unicesar.rutavida.model.Role;
import co.edu.unicesar.rutavida.model.User;
import co.edu.unicesar.rutavida.model.VehicleType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.service.AuditQueryService;
import co.edu.unicesar.rutavida.service.PanicTestSupport;
import co.edu.unicesar.rutavida.service.TrackingTestSupport;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.AuthService;
import co.edu.unicesar.rutavida.service.DashboardService;
import co.edu.unicesar.rutavida.service.ReportService;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.EmergencyService;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.FleetService;
import co.edu.unicesar.rutavida.service.HospitalService;
import co.edu.unicesar.rutavida.service.InMemoryAlertDAO;
import co.edu.unicesar.rutavida.service.InMemoryAuditDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyStationDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyTypeDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyVehicleDAO;
import co.edu.unicesar.rutavida.service.InMemoryHospitalDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.service.IncidentTestSupport;
import co.edu.unicesar.rutavida.service.RoadMapService;
import co.edu.unicesar.rutavida.service.UserService;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The administration routes of the API (A17, docs/api.md 15) through real HTTP, with in-memory
 * DAOs and EXAMPLE data:
 * <ul>
 *   <li>"admin.prueba": ADMINISTRADOR, manages users, units, stations and hospitals, and sees
 *   the traffic (this server has no simulation, so those routes answer 503);</li>
 *   <li>"operador.prueba": OPERADOR_CENTRAL, only sees units and hospitals;</li>
 *   <li>station 1 "EJEMPLO - Base de prueba", active; vehicle types 1 AMBULANCIA and 2 BOMBERO.</li>
 * </ul>
 */
class AdminApiTest {

    private static final String PASSWORD = "Clave-Segura1";
    private static final Clock CLOCK = EmergencyTestSupport.CLOCK;
    private static final List<VehicleType> TYPES = List.of(
            new VehicleType(1, "AMBULANCIA", 8, "Prueba"),
            new VehicleType(2, "BOMBERO", 8, "Prueba"));

    private final ObjectMapper json = JsonMapperFactory.create();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ApiServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        InMemoryUserDAO userDao = new InMemoryUserDAO();
        InMemoryRoleDAO roleDao = new InMemoryRoleDAO();
        roleDao.add(new Role(1, "ADMINISTRADOR", "Prueba", RecordStatus.ACTIVE, Set.of(
                new Permission(1, "USUARIOS", "CONSULTAR", "Prueba"),
                new Permission(2, "USUARIOS", "GESTIONAR", "Prueba"),
                new Permission(5, "UNIDADES", "CONSULTAR", "Prueba"),
                new Permission(6, "UNIDADES", "GESTIONAR", "Prueba"),
                new Permission(7, "HOSPITALES", "CONSULTAR", "Prueba"),
                new Permission(8, "HOSPITALES", "GESTIONAR", "Prueba"),
                new Permission(20, "AUDITORIA", "CONSULTAR", "Prueba"),
                new Permission(9, "TRAFICO", "CONSULTAR", "Prueba"))));
        roleDao.add(new Role(2, "OPERADOR_CENTRAL", "Prueba", RecordStatus.ACTIVE, Set.of(
                new Permission(5, "UNIDADES", "CONSULTAR", "Prueba"),
                new Permission(7, "HOSPITALES", "CONSULTAR", "Prueba"))));
        roleDao.add(new Role(9, "ROL_RETIRADO", "Prueba", RecordStatus.INACTIVE, Set.of()));

        PasswordHasher hasher = new PasswordHasher(1_000);
        String hash = hasher.hash(PASSWORD.toCharArray());
        LocalDateTime registered = LocalDateTime.now(CLOCK);
        userDao.insert(new User(0, 1, "PRUEBA0001", "Admin", "Prueba", "admin.prueba@ejemplo.com",
                "admin.prueba", hash, RecordStatus.ACTIVE, registered));
        userDao.insert(new User(0, 2, "PRUEBA0002", "Operador", "Prueba", "operador.prueba@ejemplo.com",
                "operador.prueba", hash, RecordStatus.ACTIVE, registered));

        InMemoryEmergencyStationDAO stationDao = new InMemoryEmergencyStationDAO();
        stationDao.insert(new EmergencyStation(0, "EJEMPLO - Base de prueba", "Calle de EJEMPLO 1", null, null,
                new GeoPoint(10.4631, -73.2532), RecordStatus.ACTIVE));

        InMemoryAuditDAO auditDao = new InMemoryAuditDAO();
        AuditService audit = new AuditService(auditDao, CLOCK);
        EmergencyTestSupport.CountingTransactions transactions = new EmergencyTestSupport.CountingTransactions();
        AuthService auth = new AuthService(userDao, roleDao, hasher, audit, CLOCK);
        SessionContext session = new SessionContext();
        AlertService alertService = new AlertService(new InMemoryAlertDAO(), userDao, roleDao, transactions, CLOCK);

        AdminControllers admin = new AdminControllers(
                new UserController(new UserService(userDao, roleDao, hasher, audit, transactions, CLOCK),
                        auth, session),
                new FleetController(new FleetService(new InMemoryEmergencyVehicleDAO(), stationDao, () -> TYPES,
                        alertService, audit, transactions), auth, session),
                new HospitalController(new HospitalService(new InMemoryHospitalDAO(), audit, transactions),
                        auth, session),
                new AuditController(new AuditQueryService(auditDao, userDao, CLOCK), auth, session));

        // The other routes are not tested here; they only need their controllers.
        EmergencyController emergencies = new EmergencyController(new EmergencyService(new InMemoryEmergencyDAO(),
                new InMemoryEmergencyTypeDAO(), new EmergencyTestSupport.FixedAssignments(),
                new EmergencyTestSupport.FixedRoutes(), audit, transactions, CLOCK), auth, session);
        DashboardController dashboard = new DashboardController(new DashboardService(
                (since, start, end) -> new DashboardSummary(0, 0, 0, 0, 0, 0, 0, null), CLOCK));
        IncidentController incidents = new IncidentController(new IncidentTestSupport().service(), auth, session);
        RoadMapController roadMap = new RoadMapController(new RoadMapService(ids -> List.of()));
        AlertController alerts = new AlertController(alertService, session);
        DispatchController dispatch = new DispatchController(new DispatchTestSupport().service(), auth, session);

        // The operations report (A23) has its own test in ApiServerTest; here it only has to exist.
        ReportController reports = new ReportController(new ReportService((first, last) -> {
            throw new UnsupportedOperationException("not used here");
        }, CLOCK));

        server = new ApiServer(new WebSessionController(auth, session, CLOCK),
                new CatalogController(new CatalogTestSupport().service()), emergencies, dashboard, reports, incidents,
                roadMap, alerts, dispatch, admin, new TrackingTestSupport().controller(auth, session),
                new PanicTestSupport().controller(auth, session), new PanicTestSupport().citizenController(), null);
        int port = freePort();
        server.start(port);
        base = "http://localhost:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void withoutTheSimulationItsRoutesAnswer503() throws Exception {
        String admin = login("admin.prueba", PASSWORD);

        HttpResponse<String> status = send("GET", "/api/simulation", admin, null);
        assertEquals(503, status.statusCode(), status.body());
        assertEquals(ApiServer.NO_SIMULATION, json.readTree(status.body()).get("message").asText());
        assertEquals(503, send("GET", "/api/traffic/segments", admin, null).statusCode());
        assertEquals(403, send("GET", "/api/simulation", login("operador.prueba", PASSWORD), null).statusCode(),
                "the permission is checked first");
    }

    @Test
    void theAdministratorConsultsTheAuditWithFilters() throws Exception {
        String admin = login("admin.prueba", PASSWORD);
        loginResponse("operador.prueba", "Clave-Equivocada9");

        HttpResponse<String> all = send("GET", "/api/audit", admin, null);
        assertEquals(200, all.statusCode(), all.body());
        JsonNode entries = json.readTree(all.body()).get("value");
        assertEquals(2, entries.size(), "the login of the admin and the failed one, last 24 hours");
        assertEquals("FALLO", entries.get(0).get("result").asText(), "newest first");
        assertEquals("admin.prueba", entries.get(1).get("username").asText());

        JsonNode failed = json.readTree(send("GET", "/api/audit?module=seguridad&result=fallo&limit=5", admin, null)
                .body()).get("value");
        assertEquals(1, failed.size());
        assertEquals("FALLO", failed.get(0).get("result").asText());

        String day = LocalDateTime.now(CLOCK).toLocalDate().toString();
        assertEquals(2, json.readTree(send("GET", "/api/audit?from=" + day + "&to=" + day, admin, null).body())
                .get("value").size(), "a whole day");

        assertEquals(400, send("GET", "/api/audit?from=14/11/2026", admin, null).statusCode());
        assertEquals(400, send("GET", "/api/audit?result=QUIZAS", admin, null).statusCode());
        assertEquals(400, send("GET", "/api/audit?limit=9000", admin, null).statusCode());
        assertEquals(400, send("GET", "/api/audit?from=2026-01-01&to=2026-11-03", admin, null).statusCode(),
                "more than 31 days");
        assertEquals(403, send("GET", "/api/audit", login("operador.prueba", PASSWORD), null).statusCode());
        assertEquals(401, send("GET", "/api/audit", null, null).statusCode());
    }

    @Test
    void theAdministratorCreatesAUserWhoCanThenLogIn() throws Exception {
        String admin = login("admin.prueba", PASSWORD);

        HttpResponse<String> roles = send("GET", "/api/roles", admin, null);
        assertEquals(200, roles.statusCode(), roles.body());
        assertEquals(2, value(roles).size(), "only the active roles");
        assertEquals("ADMINISTRADOR", value(roles).get(0).get("name").asText());

        HttpResponse<String> created = send("POST", "/api/users", admin, """
                {"roleId": 2, "document": "PRUEBA0003", "firstNames": "Nuevo", "lastNames": "Operador",
                 "email": "Nuevo.Operador@ejemplo.com", "username": "nuevo.operador", "password": "Clave-Nueva22"}""");
        assertEquals(201, created.statusCode(), created.body());
        JsonNode user = value(created);
        assertEquals("nuevo.operador", user.get("username").asText());
        assertEquals("nuevo.operador@ejemplo.com", user.get("email").asText(), "saved in lower case");
        assertEquals("A", user.get("status").asText());
        assertFalse(created.body().contains("Clave-Nueva22"), "the password never comes back");
        assertFalse(user.has("passwordHash"), "nor its hash");

        HttpResponse<String> list = send("GET", "/api/users", admin, null);
        assertEquals(3, value(list).size());

        login("nuevo.operador", "Clave-Nueva22");
    }

    @Test
    void aWrongOrRepeatedUserIsRejected() throws Exception {
        String admin = login("admin.prueba", PASSWORD);

        HttpResponse<String> shortPassword = send("POST", "/api/users", admin, """
                {"roleId": 2, "document": "PRUEBA0004", "firstNames": "A", "lastNames": "B",
                 "email": "a.b@ejemplo.com", "username": "a.b", "password": "corta1"}""");
        assertEquals(400, shortPassword.statusCode(), shortPassword.body());

        HttpResponse<String> repeated = send("POST", "/api/users", admin, """
                {"roleId": 2, "document": "PRUEBA0005", "firstNames": "Otro", "lastNames": "Operador",
                 "email": "otro@ejemplo.com", "username": "operador.prueba", "password": "Clave-Nueva22"}""");
        assertEquals(409, repeated.statusCode(), repeated.body());

        HttpResponse<String> inactiveRole = send("POST", "/api/users", admin, """
                {"roleId": 9, "document": "PRUEBA0006", "firstNames": "Otro", "lastNames": "Mas",
                 "email": "otro.mas@ejemplo.com", "username": "otro.mas", "password": "Clave-Nueva22"}""");
        assertEquals(409, inactiveRole.statusCode(), inactiveRole.body());

        assertEquals(400, send("POST", "/api/users", admin, "").statusCode());
    }

    @Test
    void aDeactivatedUserCannotLogInUntilActivatedAgain() throws Exception {
        String admin = login("admin.prueba", PASSWORD);

        HttpResponse<String> off = send("PATCH", "/api/users/2/status", admin, "{\"status\": \"I\"}");
        assertEquals(200, off.statusCode(), off.body());
        assertEquals("I", value(off).get("status").asText());
        assertEquals(401, loginResponse("operador.prueba", PASSWORD).statusCode());

        HttpResponse<String> on = send("PATCH", "/api/users/2/status", admin, "{\"status\": \"A\"}");
        assertEquals(200, on.statusCode(), on.body());
        login("operador.prueba", PASSWORD);

        assertEquals(400, send("PATCH", "/api/users/2/status", admin, "{\"status\": \"X\"}").statusCode());
        assertEquals(400, send("PATCH", "/api/users/2/status", admin, "{}").statusCode());
        assertEquals(404, send("PATCH", "/api/users/99/status", admin, "{\"status\": \"I\"}").statusCode());
    }

    @Test
    void anOperatorCanSeeButNotManage() throws Exception {
        String operator = login("operador.prueba", PASSWORD);

        assertEquals(403, send("GET", "/api/users", operator, null).statusCode());
        assertEquals(403, send("GET", "/api/roles", operator, null).statusCode());
        assertEquals(403, send("POST", "/api/units", operator,
                "{\"vehicleTypeId\": 1, \"stationId\": 1, \"unitCode\": \"EJEMPLO-AMB-09\"}").statusCode());
        assertEquals(403, send("POST", "/api/hospitals", operator, "{\"name\": \"X\"}").statusCode());
        assertEquals(401, send("GET", "/api/users", null, null).statusCode());
    }

    @Test
    void theAdministratorManagesUnitsAndStations() throws Exception {
        String admin = login("admin.prueba", PASSWORD);

        HttpResponse<String> station = send("POST", "/api/stations", admin, """
                {"name": "EJEMPLO - Base norte", "address": "Calle de EJEMPLO 2",
                 "location": {"latitude": 10.4700, "longitude": -73.2500}}""");
        assertEquals(201, station.statusCode(), station.body());
        long stationId = value(station).get("id").asLong();

        HttpResponse<String> unit = send("POST", "/api/units", admin, """
                {"vehicleTypeId": 1, "stationId": 1, "unitCode": "ejemplo-amb-09", "plate": "EJM109"}""");
        assertEquals(201, unit.statusCode(), unit.body());
        long unitId = value(unit).get("id").asLong();
        assertEquals("EJEMPLO-AMB-09", value(unit).get("unitCode").asText());
        assertEquals("DISPONIBLE", value(unit).get("operationalStatus").asText());

        HttpResponse<String> moved = send("PUT", "/api/units/" + unitId, admin, """
                {"vehicleTypeId": 1, "stationId": %d, "unitCode": "EJEMPLO-AMB-09", "plate": "EJM209"}"""
                .formatted(stationId));
        assertEquals(200, moved.statusCode(), moved.body());
        assertEquals(stationId, value(moved).get("stationId").asLong());
        assertEquals("EJM209", value(moved).get("plate").asText());

        HttpResponse<String> maintenance = send("PATCH", "/api/units/" + unitId + "/status", admin,
                "{\"status\": \"MANTENIMIENTO\"}");
        assertEquals(200, maintenance.statusCode(), maintenance.body());
        assertEquals("MANTENIMIENTO", value(maintenance).get("operationalStatus").asText());

        HttpResponse<String> busy = send("PATCH", "/api/units/" + unitId + "/status", admin,
                "{\"status\": \"OCUPADO\"}");
        assertEquals(409, busy.statusCode(), "OCUPADO is never set by hand: " + busy.body());

        HttpResponse<String> closed = send("PATCH", "/api/stations/" + stationId + "/status", admin,
                "{\"status\": \"I\"}");
        assertEquals(200, closed.statusCode(), closed.body());
        assertEquals("I", value(closed).get("status").asText());
    }

    @Test
    void theAdministratorManagesHospitals() throws Exception {
        String admin = login("admin.prueba", PASSWORD);

        HttpResponse<String> created = send("POST", "/api/hospitals", admin, """
                {"name": "Hospital de EJEMPLO", "level": "II", "address": "Calle de EJEMPLO 3",
                 "location": {"latitude": 10.4690, "longitude": -73.2540}, "emergencyCapacity": 12}""");
        assertEquals(201, created.statusCode(), created.body());
        long id = value(created).get("id").asLong();
        assertEquals("II", value(created).get("level").asText());

        HttpResponse<String> edited = send("PUT", "/api/hospitals/" + id, admin, """
                {"name": "Hospital de EJEMPLO dos", "level": "III", "address": "Calle de EJEMPLO 3",
                 "location": {"latitude": 10.4690, "longitude": -73.2540}, "emergencyCapacity": 15}""");
        assertEquals(200, edited.statusCode(), edited.body());
        assertEquals("III", value(edited).get("level").asText());

        HttpResponse<String> off = send("PATCH", "/api/hospitals/" + id + "/status", admin, "{\"status\": \"I\"}");
        assertEquals(200, off.statusCode(), off.body());
        assertEquals("I", value(off).get("status").asText());

        assertEquals(400, send("POST", "/api/hospitals", admin, "{\"name\": \"Sin nivel\", \"level\": \"IX\"}")
                .statusCode());
    }

    @Test
    void anyUserChangesHisOwnPassword() throws Exception {
        String operator = login("operador.prueba", PASSWORD);

        HttpResponse<String> wrong = send("POST", "/api/auth/password", operator,
                "{\"currentPassword\": \"Otra-Clave99\", \"newPassword\": \"Clave-Nueva22\"}");
        assertEquals(400, wrong.statusCode(), wrong.body());
        assertEquals(400, send("POST", "/api/auth/password", operator, "{}").statusCode());

        HttpResponse<String> changed = send("POST", "/api/auth/password", operator,
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"Clave-Nueva22\"}");
        assertEquals(200, changed.statusCode(), changed.body());

        assertEquals(401, loginResponse("operador.prueba", PASSWORD).statusCode());
        login("operador.prueba", "Clave-Nueva22");
        assertEquals(401, send("POST", "/api/auth/password", null, "{}").statusCode());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
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

    private HttpResponse<String> loginResponse(String username, String password) throws Exception {
        return send("POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private String login(String username, String password) throws Exception {
        HttpResponse<String> response = loginResponse(username, password);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("value").get("token").asText();
    }

    private JsonNode value(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).get("value");
    }
}
