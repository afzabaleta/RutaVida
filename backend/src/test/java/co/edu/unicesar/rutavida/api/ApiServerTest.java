package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.AlertController;
import co.edu.unicesar.rutavida.controller.CatalogTestSupport;
import co.edu.unicesar.rutavida.controller.DispatchController;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.IncidentController;
import co.edu.unicesar.rutavida.controller.RoadMapController;
import co.edu.unicesar.rutavida.controller.WebTestSupport;
import co.edu.unicesar.rutavida.controller.DashboardController;
import co.edu.unicesar.rutavida.dto.SegmentView;
import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.model.Alert;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.service.AlertService;
import co.edu.unicesar.rutavida.model.GeoPoint;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.DispatchTestSupport;
import co.edu.unicesar.rutavida.service.EmergencyService;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryAlertDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyTypeDAO;
import co.edu.unicesar.rutavida.service.InMemoryRoleDAO;
import co.edu.unicesar.rutavida.service.InMemoryUserDAO;
import co.edu.unicesar.rutavida.service.IncidentTestSupport;
import co.edu.unicesar.rutavida.service.RoadMapService;
import co.edu.unicesar.rutavida.service.DashboardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.edu.unicesar.rutavida.dto.DashboardSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real HTTP server on a free port of this computer, with the EXAMPLE users of
 * WebTestSupport, the in-memory catalogs of CatalogTestSupport and EmergencyService on
 * in-memory DAOs, a dashboard with fixed counts, the incidents of IncidentTestSupport and the
 * dispatch on the small city of DispatchTestSupport. Checks the contract of docs/api.md
 * (sections 1, 2, 4, 5, 6, 7, 9 and 11) as the web will see it. Does not need Oracle.
 */
class ApiServerTest {

    /** Body of POST /api/emergencies, like the web will send it (docs/api.md, 5.3). */
    private static final String NEW_EMERGENCY = """
            {"emergencyTypeId": 3, "addressReference": "Frente al Parque La Popa",
             "location": {"latitude": 10.467172, "longitude": -73.265099},
             "severity": "CRITICA", "description": "Choque de prueba"}""";

    /** Body of POST /api/incidents (docs/api.md, 9.2). */
    private static final String NEW_INCIDENT = """
            {"segmentId": 413, "type": "ACCIDENTE", "severity": "ALTA", "blocksSegment": false,
             "description": "Choque en la Calle 16"}""";

    /** The only segment the map of these tests knows (EXAMPLE coordinates). */
    private static final SegmentView CALLE_16 = new SegmentView(IncidentTestSupport.SEGMENT_ID, "Calle 16",
            new GeoPoint(10.468901, -73.258004), new GeoPoint(10.468377, -73.260112));

    @TempDir
    Path frontend;

    private final ObjectMapper json = JsonMapperFactory.create();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final CatalogTestSupport catalogs = new CatalogTestSupport();
    private InMemoryEmergencyDAO emergencyDao;
    private WebTestSupport support;
    private DispatchTestSupport city;
    private long martaFirstAlert;
    private long anaAlert;
    private ApiServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        Files.writeString(frontend.resolve("index.html"), "<h1>RutaVida de prueba</h1>", StandardCharsets.UTF_8);
        support = new WebTestSupport();

        InMemoryEmergencyTypeDAO types = new InMemoryEmergencyTypeDAO();
        types.add(new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Accidente de transito con posibles lesionados"));
        emergencyDao = new InMemoryEmergencyDAO();
        EmergencyService emergencyService = new EmergencyService(emergencyDao, types,
                new EmergencyTestSupport.FixedAssignments(), new EmergencyTestSupport.FixedRoutes(),
                new AuditService(support.auditDao, support.clock),
                new EmergencyTestSupport.CountingTransactions(), support.clock);
        EmergencyController emergencies = new EmergencyController(emergencyService, support.authService,
                support.sessionContext);

        // EXAMPLE counts; no unit has arrived today, so there is no average response time.
        DashboardController dashboard = new DashboardController(new DashboardService(
                (since, start, end) -> new DashboardSummary(3, 3, 1, 2, 1, 4, 0, null), support.clock));

        // Dispatch on the small city of DispatchTestSupport (K7): 3 x 3 test streets, EXAMPLE units.
        city = new DispatchTestSupport();
        DispatchController dispatch = new DispatchController(city.service(), support.authService,
                support.sessionContext);

        // Incidents on in-memory DAOs (S7); the map knows CALLE_16 and the streets of the city.
        IncidentController incidents = new IncidentController(new IncidentTestSupport().service(),
                support.authService, support.sessionContext);
        RoadMapController roadMap = new RoadMapController(new RoadMapService(this::segmentViews));

        // EXAMPLE alerts: two for marta.ruiz (user 3) and one for ana.perez (user 1).
        InMemoryAlertDAO alertDao = new InMemoryAlertDAO();
        LocalDateTime now = LocalDateTime.now(support.clock);
        martaFirstAlert = alertDao.insert(new Alert(0, 15L, 3, AlertType.ROUTE_RECALCULATION,
                "Se recalculó la ruta de EJEMPLO-AMB-01: llega 1,5 min antes.", now.minusMinutes(10), false));
        alertDao.insert(new Alert(0, null, 3, AlertType.AVAILABILITY_CHANGE,
                "La unidad EJEMPLO-AMB-02 pasó de DISPONIBLE a MANTENIMIENTO.", now.minusMinutes(2), false));
        anaAlert = alertDao.insert(new Alert(0, null, 1, AlertType.OTHER,
                "Alerta de EJEMPLO de otra usuaria.", now.minusMinutes(1), false));
        AlertController alerts = new AlertController(new AlertService(alertDao, new InMemoryUserDAO(),
                new InMemoryRoleDAO(), new EmergencyTestSupport.CountingTransactions(), support.clock),
                support.sessionContext);

        server = new ApiServer(support.sessions(), new CatalogController(catalogs.service()), emergencies,
                dashboard, incidents, roadMap, alerts, dispatch, frontend);
        int port = freePort();
        server.start(port);
        base = "http://localhost:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    /** The fake RoadMapDAO: CALLE_16 and the segments of the small city of the dispatch tests. */
    private List<SegmentView> segmentViews(Collection<Long> ids) {
        List<SegmentView> views = new ArrayList<>();
        for (long id : ids) {
            if (id == CALLE_16.segmentId()) {
                views.add(CALLE_16);
            }
            int index = city.graph.edgeIndexOf(id);
            if (index >= 0) {
                Edge edge = city.graph.edge(index);
                Node from = city.graph.node(edge.from());
                Node to = city.graph.node(edge.to());
                views.add(new SegmentView(id, edge.name(), new GeoPoint(from.lat(), from.lon()),
                        new GeoPoint(to.lat(), to.lon())));
            }
        }
        return views;
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

    private String login(String username) throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + WebTestSupport.PASSWORD + "\"}");
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("value").get("token").asText();
    }

    private JsonNode value(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).get("value");
    }

    // ------------------------------------------------------------ session (A12a)

    @Test
    void loginAnswersTheTokenInsideTheCommonEnvelope() throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/login", null,
                "{\"username\":\"ana.perez\",\"password\":\"" + WebTestSupport.PASSWORD + "\"}");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode body = json.readTree(response.body());
        assertTrue(body.get("success").asBoolean());
        assertTrue(body.get("message").isNull());
        JsonNode value = body.get("value");
        assertTrue(value.get("token").asText().matches("[0-9a-f]{64}"));
        assertEquals("2026-11-05T17:00:00", value.get("expiresAt").asText());
        assertEquals(30, value.get("idleTimeoutMinutes").asInt());
        assertEquals("OPERADOR_CENTRAL", value.get("role").asText());
        assertFalse(response.body().contains("passwordHash"), "the hash never leaves the server");
        assertFalse(response.body().contains("Clave-Segura1"));
    }

    @Test
    void badLoginsAre401And400() throws Exception {
        HttpResponse<String> wrong = send("POST", "/api/auth/login", null,
                "{\"username\":\"ana.perez\",\"password\":\"otra\"}");
        assertEquals(401, wrong.statusCode());
        JsonNode body = json.readTree(wrong.body());
        assertFalse(body.get("success").asBoolean());
        assertEquals("Usuario o contraseña incorrectos", body.get("message").asText());
        assertTrue(body.get("value").isNull());

        assertEquals(400, send("POST", "/api/auth/login", null, "{\"username\":").statusCode(), "malformed JSON");
        assertEquals(400, send("POST", "/api/auth/login", null, null).statusCode(), "no body");
        assertEquals(400, send("POST", "/api/auth/login", null, "{\"username\":\"ana.perez\"}").statusCode(),
                "no password");
    }

    @Test
    void protectedRoutesNeedTokenAndPermission() throws Exception {
        HttpResponse<String> anonymous = send("GET", "/api/emergency-types", null, null);
        assertEquals(401, anonymous.statusCode());
        assertEquals("Debe iniciar sesión.", json.readTree(anonymous.body()).get("message").asText());
        assertEquals(401, send("GET", "/api/emergency-types", "no-es-un-token", null).statusCode());

        HttpResponse<String> withoutPermission = send("GET", "/api/emergency-types", login("luis.gomez"), null);
        assertEquals(403, withoutPermission.statusCode());

        HttpResponse<String> allowed = send("GET", "/api/emergency-types", login("ana.perez"), null);
        assertEquals(200, allowed.statusCode(), allowed.body());
        JsonNode types = value(allowed);
        assertEquals(2, types.size());
        assertEquals("EMERGENCIA_MEDICA", types.get(0).get("name").asText());
        assertEquals(9, types.get(0).get("priority").asInt());
    }

    @Test
    void sessionAndLogout() throws Exception {
        String token = login("ana.perez");

        HttpResponse<String> session = send("GET", "/api/auth/session", token, null);
        assertEquals(200, session.statusCode());
        assertTrue(value(session).get("token").isNull(), "the session does not repeat the token");
        assertEquals("ana.perez", value(session).get("user").get("username").asText());

        assertEquals(200, send("POST", "/api/auth/logout", token, null).statusCode());
        assertEquals(401, send("GET", "/api/auth/session", token, null).statusCode());
        assertEquals(401, send("GET", "/api/emergency-types", token, null).statusCode());
    }

    @Test
    void oracleDownIs503WithoutTechnicalDetails() throws Exception {
        String token = login("ana.perez");
        catalogs.oracleDown.set(true);

        HttpResponse<String> response = send("GET", "/api/emergency-types", token, null);

        assertEquals(503, response.statusCode());
        assertEquals("No hay conexión con la base de datos.", json.readTree(response.body()).get("message").asText());
        assertFalse(response.body().contains("ORA-"));
    }

    @Test
    void thePagesOfTheFrontendAreServedFromTheSameAddress() throws Exception {
        HttpResponse<String> page = send("GET", "/index.html", null, null);

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("RutaVida de prueba"));
    }

    // ------------------------------------------------------------ emergencies (A12b)

    @Test
    void anEmergencyIsRegisteredListedFoundAndItsHistoryRead() throws Exception {
        String token = login("marta.ruiz");

        HttpResponse<String> created = send("POST", "/api/emergencies", token, NEW_EMERGENCY);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode emergency = value(created);
        long id = emergency.get("id").asLong();
        assertTrue(id > 0);
        assertEquals("REPORTADA", emergency.get("status").asText());
        assertEquals("CRITICA", emergency.get("severity").asText());
        assertEquals(3, emergency.get("reportedByUserId").asLong(), "the user of the token, not one sent by the web");
        assertEquals("2026-11-05T09:00:00", emergency.get("reportedAt").asText(), "the clock of the server");
        assertEquals(10.467172, emergency.get("location").get("latitude").asDouble());

        HttpResponse<String> active = send("GET", "/api/emergencies", token, null);
        assertEquals(200, active.statusCode());
        assertEquals(id, value(active).get(0).get("id").asLong());

        HttpResponse<String> one = send("GET", "/api/emergencies/" + id, token, null);
        assertEquals(200, one.statusCode());
        assertEquals("Frente al Parque La Popa", value(one).get("addressReference").asText());

        HttpResponse<String> history = send("GET", "/api/emergencies/" + id + "/history", token, null);
        assertEquals(200, history.statusCode(), history.body());
        assertEquals(id, value(history).get("emergency").get("id").asLong());
        assertEquals("REGISTRAR", value(history).get("auditTrail").get(0).get("operation").asText());
        assertEquals("EXITO", value(history).get("auditTrail").get(0).get("result").asText());
    }

    @Test
    void wrongEmergencyRequestsAre400403And404() throws Exception {
        String marta = login("marta.ruiz");

        HttpResponse<String> javaName = send("POST", "/api/emergencies", marta,
                NEW_EMERGENCY.replace("\"CRITICA\"", "\"CRITICAL\""));
        assertEquals(400, javaName.statusCode(), "only the Spanish code is accepted");

        HttpResponse<String> noAddress = send("POST", "/api/emergencies", marta,
                NEW_EMERGENCY.replace("Frente al Parque La Popa", " "));
        assertEquals(400, noAddress.statusCode());
        assertEquals("El campo dirección de referencia es obligatorio.",
                json.readTree(noAddress.body()).get("message").asText());

        HttpResponse<String> unknownType = send("POST", "/api/emergencies", marta,
                NEW_EMERGENCY.replace("\"emergencyTypeId\": 3", "\"emergencyTypeId\": 99"));
        assertEquals(400, unknownType.statusCode());

        assertEquals(403, send("POST", "/api/emergencies", login("luis.gomez"), NEW_EMERGENCY).statusCode());
        assertEquals(404, send("GET", "/api/emergencies/999", marta, null).statusCode());
        assertEquals(404, send("GET", "/api/emergencies/999/history", marta, null).statusCode());
        assertEquals(400, send("GET", "/api/emergencies/abc", marta, null).statusCode());
        assertTrue(emergencyDao.saved.isEmpty(), "nothing was saved");
    }

    // ------------------------------------------------------------ resources (A12b)

    @Test
    void theCatalogsOfUnitsStationsAndHospitals() throws Exception {
        String token = login("marta.ruiz");

        JsonNode units = value(send("GET", "/api/units", token, null));
        assertEquals(2, units.size());
        assertEquals("EJEMPLO-AMB-01", units.get(0).get("unitCode").asText());
        assertEquals("DISPONIBLE", units.get(0).get("operationalStatus").asText());
        assertTrue(units.get(1).get("lastLocation").isNull(), "a unit without position");

        assertEquals("AMBULANCIA", value(send("GET", "/api/vehicle-types", token, null)).get(0).get("name").asText());
        assertEquals("A", value(send("GET", "/api/stations", token, null)).get(0).get("status").asText());

        JsonNode operational = value(send("GET", "/api/hospitals", token, null));
        assertEquals(1, operational.size());
        assertEquals("II", operational.get(0).get("level").asText());
        assertEquals(2, value(send("GET", "/api/hospitals?all=true", token, null)).size());

        assertEquals(403, send("GET", "/api/units", login("luis.gomez"), null).statusCode(),
                "a role without UNIDADES / CONSULTAR");
    }


    // ------------------------------------------------------------ dashboard (A14)

    @Test
    void theDashboardSummaryKeepsNullWhenThereIsNoAverage() throws Exception {
        HttpResponse<String> response = send("GET", "/api/dashboard/summary", login("marta.ruiz"), null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode summary = value(response);
        assertEquals(3, summary.get("activeEmergencies").asInt());
        assertEquals(3, summary.get("availableUnits").asInt());
        assertEquals(1, summary.get("busyUnits").asInt());
        assertEquals(2, summary.get("operationalHospitals").asInt());
        assertEquals(1, summary.get("activeIncidents").asInt());
        assertEquals(4, summary.get("congestedSegments").asInt());
        assertEquals(0, summary.get("closedToday").asInt());
        assertTrue(summary.has("averageResponseMinutes"), "the field is sent");
        assertTrue(summary.get("averageResponseMinutes").isNull(), "null, never 0 (docs/api.md 11.1)");

        assertEquals(403, send("GET", "/api/dashboard/summary", login("luis.gomez"), null).statusCode(),
                "a role without REPORTES / CONSULTAR");
        assertEquals(401, send("GET", "/api/dashboard/summary", null, null).statusCode());
    }

    // ------------------------------------------------------------ road incidents (A12c)

    @Test
    void anIncidentIsReportedValidatedAndListedWithItsSegment() throws Exception {
        String token = login("marta.ruiz");

        HttpResponse<String> reported = send("POST", "/api/incidents", token, NEW_INCIDENT);
        assertEquals(201, reported.statusCode(), reported.body());
        JsonNode incident = value(reported);
        long id = incident.get("id").asLong();
        assertEquals("PENDIENTE", incident.get("status").asText(), "it does not change the routes yet");
        assertEquals("OPERADOR", incident.get("origin").asText());
        assertEquals("ACCIDENTE", incident.get("type").asText());
        assertEquals("Calle 16", incident.get("segment").get("streetName").asText());
        assertEquals(10.468901, incident.get("segment").get("from").get("latitude").asDouble());

        assertEquals(1, value(send("GET", "/api/incidents", token, null)).size());
        assertEquals(0, value(send("GET", "/api/incidents?status=ACTIVO", token, null)).size());

        HttpResponse<String> active = send("PATCH", "/api/incidents/" + id + "/status", token,
                "{\"status\": \"ACTIVO\"}");
        assertEquals(200, active.statusCode(), active.body());
        assertEquals("ACTIVO", value(active).get("status").asText());
        assertEquals(1, value(send("GET", "/api/incidents?status=ACTIVO", token, null)).size());

        assertEquals(409, send("PATCH", "/api/incidents/" + id + "/status", token,
                "{\"status\": \"RECHAZADO\"}").statusCode(), "an active incident is closed, not rejected");
        HttpResponse<String> closed = send("PATCH", "/api/incidents/" + id + "/status", token,
                "{\"status\": \"CERRADO\"}");
        assertEquals("CERRADO", value(closed).get("status").asText());
        assertTrue(value(send("GET", "/api/incidents", token, null)).isEmpty(), "a closed incident is not open");
    }

    @Test
    void wrongIncidentRequestsAre400403And404() throws Exception {
        String token = login("marta.ruiz");

        assertEquals(400, send("POST", "/api/incidents", token,
                        "{\"segmentId\": 999, \"type\": \"ACCIDENTE\", \"severity\": \"ALTA\"}").statusCode(),
                "the segment does not exist");
        assertEquals(400, send("POST", "/api/incidents", token,
                        "{\"segmentId\": 413, \"type\": \"METEORITO\", \"severity\": \"ALTA\"}").statusCode(),
                "unknown type");
        assertEquals(400, send("GET", "/api/incidents?status=VOLANDO", token, null).statusCode());
        assertEquals(400, send("PATCH", "/api/incidents/1/status", token, "{}").statusCode(), "no status");
        assertEquals(404, send("PATCH", "/api/incidents/999/status", token,
                "{\"status\": \"ACTIVO\"}").statusCode());
        assertEquals(403, send("GET", "/api/incidents", login("ana.perez"), null).statusCode(),
                "the test operator has no INCIDENTES permission");
        assertEquals(401, send("GET", "/api/incidents", null, null).statusCode());
    }


    // ------------------------------------------------------------ dispatch and routes (A12d)

    @Test
    void aUnitIsDispatchedAndFollowsItsMissionWithTheRoutesOnTheMap() throws Exception {
        String token = login("marta.ruiz");
        long emergency = city.report(DispatchTestSupport.MEDICAL, 1, 2);
        String body = "{\"emergencyId\": " + emergency + ", \"vehicleId\": " + DispatchTestSupport.AMBULANCE + "}";

        HttpResponse<String> listed = send("GET", "/api/emergencies/" + emergency + "/candidates", token, null);
        assertEquals(200, listed.statusCode(), listed.body());
        JsonNode candidates = value(listed);
        assertEquals(DispatchTestSupport.AMBULANCE, candidates.get(0).get("vehicleId").asLong(), "the fastest first");
        assertEquals("AMBULANCIA", candidates.get(0).get("vehicleType").asText());
        assertTrue(candidates.get(0).get("etaMinutes").asDouble() > 0);

        HttpResponse<String> preview = send("POST", "/api/routes/preview", token, body);
        assertEquals(200, preview.statusCode(), preview.body());
        JsonNode option = value(preview).get(0);
        assertEquals(1, option.get("rank").asInt());
        assertEquals("PRINCIPAL", option.get("type").asText());
        assertEquals(option.get("segmentIds").size() + 1, option.get("path").size());
        assertTrue(city.routes.findByEmergency(emergency).isEmpty(), "the preview saves nothing");

        HttpResponse<String> dispatched = send("POST", "/api/dispatches", token, body);
        assertEquals(201, dispatched.statusCode(), dispatched.body());
        JsonNode assignment = value(dispatched).get("assignment");
        long mission = assignment.get("id").asLong();
        assertEquals("ASIGNADA", assignment.get("status").asText());
        assertEquals(3, assignment.get("coordinatorUserId").asLong(), "marta.ruiz, the user of the token");
        JsonNode routes = value(dispatched).get("routes");
        JsonNode main = routes.get(0);
        assertEquals("ACTIVA", main.get("status").asText());
        assertTrue(main.get("recommended").asBoolean());
        assertEquals(1, main.get("rank").asInt());
        assertEquals(main.get("segmentIds").size() + 1, main.get("path").size(), "the line to draw");
        assertEquals(10.462, main.get("path").get(0).get("latitude").asDouble(), 1e-9, "it starts at the unit");
        assertTrue(main.get("explanation").asText().startsWith("Ruta recomendada"));
        assertFalse(main.has("segments"), "the RUTA_TRAMO rows do not travel");
        for (int i = 1; i < routes.size(); i++) {
            assertEquals("PROPUESTA", routes.get(i).get("status").asText());
            assertEquals(i + 1, routes.get(i).get("rank").asInt());
        }

        HttpResponse<String> current = send("GET", "/api/dispatches/" + mission + "/routes", token, null);
        assertEquals(200, current.statusCode(), current.body());
        assertEquals(routes.size(), value(current).size());
        assertEquals(main.get("routeId").asLong(), value(current).get(0).get("routeId").asLong());

        HttpResponse<String> enRoute = send("PATCH", "/api/dispatches/" + mission + "/status", token,
                "{\"status\": \"EN_RUTA\"}");
        assertEquals(200, enRoute.statusCode(), enRoute.body());
        assertEquals("EN_RUTA", value(enRoute).get("status").asText());
        assertFalse(value(enRoute).get("departedAt").isNull());

        HttpResponse<String> onScene = send("PATCH", "/api/dispatches/" + mission + "/status", token,
                "{\"status\": \"EN_SITIO\"}");
        assertEquals("EN_SITIO", value(onScene).get("status").asText());
        assertTrue(value(send("GET", "/api/dispatches/" + mission + "/routes", token, null)).isEmpty(),
                "the routes to the scene ended when the unit arrived");
    }

    @Test
    void wrongDispatchRequestsAre400403404And409() throws Exception {
        String token = login("marta.ruiz");
        long emergency = city.report(DispatchTestSupport.MEDICAL, 1, 2);

        assertEquals(400, send("POST", "/api/dispatches", token, "{}").statusCode(), "no ids");
        assertEquals(400, send("POST", "/api/routes/preview", token,
                "{\"emergencyId\": " + emergency + "}").statusCode(), "no unit");
        assertEquals(400, send("POST", "/api/dispatches", token, "{\"emergencyId\":").statusCode(), "malformed");
        assertEquals(404, send("GET", "/api/emergencies/999/candidates", token, null).statusCode());
        assertEquals(409, send("POST", "/api/dispatches", token, "{\"emergencyId\": " + emergency
                + ", \"vehicleId\": " + DispatchTestSupport.BUSY_AMBULANCE + "}").statusCode(), "a busy unit");
        assertEquals(409, send("POST", "/api/dispatches", token, "{\"emergencyId\": " + emergency
                + ", \"vehicleId\": " + DispatchTestSupport.FIRE_TRUCK + "}").statusCode(), "a fire truck to a medical one");

        HttpResponse<String> dispatched = send("POST", "/api/dispatches", token, "{\"emergencyId\": " + emergency
                + ", \"vehicleId\": " + DispatchTestSupport.AMBULANCE + "}");
        assertEquals(201, dispatched.statusCode(), dispatched.body());
        long mission = value(dispatched).get("assignment").get("id").asLong();
        assertEquals(409, send("POST", "/api/dispatches", token, "{\"emergencyId\": " + emergency
                        + ", \"vehicleId\": " + DispatchTestSupport.NO_POSITION_AMBULANCE + "}").statusCode(),
                "the emergency is no longer REPORTADA");

        String path = "/api/dispatches/" + mission + "/status";
        assertEquals(409, send("PATCH", path, token, "{\"status\": \"FINALIZADA\"}").statusCode(),
                "ASIGNADA cannot jump to FINALIZADA");
        assertEquals(400, send("PATCH", path, token, "{\"status\": \"TRASLADANDO\"}").statusCode(),
                "the transfer needs CU-11 (K7b)");
        assertEquals(400, send("PATCH", path, token, "{\"status\": \"VOLANDO\"}").statusCode());
        assertEquals(400, send("PATCH", path, token, "{}").statusCode(), "no status");
        assertEquals(404, send("GET", "/api/dispatches/999/routes", token, null).statusCode());

        String ana = login("ana.perez");
        assertEquals(403, send("GET", "/api/emergencies/" + emergency + "/candidates", ana, null).statusCode(),
                "the test operator has no DESPACHO permission");
        assertEquals(403, send("GET", "/api/dispatches/" + mission + "/routes", ana, null).statusCode(),
                "nor RUTAS");
        assertEquals(401, send("POST", "/api/dispatches", null, "{}").statusCode());
    }

    // ------------------------------------------------------------ alerts (A12f)

    @Test
    void eachUserReadsOnlyHisAlertsAndMarksThemAsRead() throws Exception {
        String token = login("marta.ruiz");

        HttpResponse<String> listed = send("GET", "/api/alerts", token, null);
        assertEquals(200, listed.statusCode(), listed.body());
        JsonNode alerts = value(listed);
        assertEquals(2, alerts.size(), "not the alert of ana.perez");
        assertEquals("CAMBIO_DISPONIBILIDAD", alerts.get(0).get("type").asText(), "newest first");
        assertTrue(alerts.get(0).get("emergencyId").isNull(), "a general alert");
        assertEquals("RECALCULO_RUTA", alerts.get(1).get("type").asText());
        assertEquals(15, alerts.get(1).get("emergencyId").asLong());
        assertEquals("2026-11-05T08:50:00", alerts.get(1).get("createdAt").asText());
        assertFalse(alerts.get(1).get("read").asBoolean());

        HttpResponse<String> read = send("PATCH", "/api/alerts/" + martaFirstAlert + "/read", token, null);
        assertEquals(200, read.statusCode(), read.body());
        assertTrue(value(read).get("read").asBoolean());

        JsonNode unread = value(send("GET", "/api/alerts?unread=true", token, null));
        assertEquals(1, unread.size());
        assertEquals("CAMBIO_DISPONIBILIDAD", unread.get(0).get("type").asText());
        assertEquals(2, value(send("GET", "/api/alerts", token, null)).size(), "the read one is still listed");
    }

    @Test
    void alertsNeedOnlyASessionAndTheAlertOfAnotherUserIs404() throws Exception {
        String marta = login("marta.ruiz");

        assertEquals(404, send("PATCH", "/api/alerts/" + anaAlert + "/read", marta, null).statusCode(),
                "the alert of another user");
        assertEquals(404, send("PATCH", "/api/alerts/999/read", marta, null).statusCode());
        assertEquals(400, send("PATCH", "/api/alerts/abc/read", marta, null).statusCode());

        HttpResponse<String> noPermissions = send("GET", "/api/alerts", login("luis.gomez"), null);
        assertEquals(200, noPermissions.statusCode(), "a role without permissions still reads its alerts");
        assertTrue(value(noPermissions).isEmpty());
        assertEquals(401, send("GET", "/api/alerts", null, null).statusCode());
    }

    @Test
    void readingTheAlertsDoesNotKeepTheSessionAlive() throws Exception {
        String token = login("marta.ruiz");

        support.clock.advance(Duration.ofMinutes(20));
        assertEquals(200, send("GET", "/api/alerts", token, null).statusCode());
        support.clock.advance(Duration.ofMinutes(15));

        assertEquals(401, send("GET", "/api/alerts", token, null).statusCode(),
                "35 minutes without a request of the user (DT-08)");
    }
}