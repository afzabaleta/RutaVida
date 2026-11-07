package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.CatalogTestSupport;
import co.edu.unicesar.rutavida.controller.EmergencyController;
import co.edu.unicesar.rutavida.controller.WebTestSupport;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.service.AuditService;
import co.edu.unicesar.rutavida.service.EmergencyService;
import co.edu.unicesar.rutavida.service.EmergencyTestSupport;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyDAO;
import co.edu.unicesar.rutavida.service.InMemoryEmergencyTypeDAO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real HTTP server on a free port of this computer, with the EXAMPLE users of
 * WebTestSupport, the in-memory catalogs of CatalogTestSupport and EmergencyService on
 * in-memory DAOs. Checks the contract of docs/api.md (sections 1, 2, 4, 5 and 7) as the web
 * will see it. Does not need Oracle.
 */
class ApiServerTest {

    /** Body of POST /api/emergencies, like the web will send it (docs/api.md, 5.3). */
    private static final String NEW_EMERGENCY = """
            {"emergencyTypeId": 3, "addressReference": "Frente al Parque La Popa",
             "location": {"latitude": 10.467172, "longitude": -73.265099},
             "severity": "CRITICA", "description": "Choque de prueba"}""";

    @TempDir
    Path frontend;

    private final ObjectMapper json = JsonMapperFactory.create();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final CatalogTestSupport catalogs = new CatalogTestSupport();
    private InMemoryEmergencyDAO emergencyDao;
    private ApiServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        Files.writeString(frontend.resolve("index.html"), "<h1>RutaVida de prueba</h1>", StandardCharsets.UTF_8);
        WebTestSupport support = new WebTestSupport();

        InMemoryEmergencyTypeDAO types = new InMemoryEmergencyTypeDAO();
        types.add(new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Accidente de transito con posibles lesionados"));
        emergencyDao = new InMemoryEmergencyDAO();
        EmergencyService emergencyService = new EmergencyService(emergencyDao, types,
                new EmergencyTestSupport.FixedAssignments(), new EmergencyTestSupport.FixedRoutes(),
                new AuditService(support.auditDao, support.clock),
                new EmergencyTestSupport.CountingTransactions(), support.clock);
        EmergencyController emergencies = new EmergencyController(emergencyService, support.authService,
                support.sessionContext);

        server = new ApiServer(support.sessions(), new CatalogController(catalogs.service()), emergencies, frontend);
        int port = freePort();
        server.start(port);
        base = "http://localhost:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
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
}