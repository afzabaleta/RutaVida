package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.CatalogController;
import co.edu.unicesar.rutavida.controller.WebTestSupport;
import co.edu.unicesar.rutavida.dao.EmergencyTypeDAO;
import co.edu.unicesar.rutavida.exception.DataAccessException;
import co.edu.unicesar.rutavida.model.EmergencyType;
import co.edu.unicesar.rutavida.service.CatalogService;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real HTTP server on a free port of this computer, with the EXAMPLE users of
 * WebTestSupport and an in-memory catalog. Checks the contract of docs/api.md
 * (sections 1.1, 1.2, 2, 4 and 5.1) as the web will see it. Does not need Oracle.
 */
class ApiServerTest {

    private static final List<EmergencyType> TYPES = List.of(
            new EmergencyType(1, "EMERGENCIA_MEDICA", 9, "Urgencia medica sin accidente de transito"),
            new EmergencyType(3, "ACCIDENTE_VIAL", 8, "Accidente de transito con posibles lesionados"));

    @TempDir
    Path frontend;

    private final ObjectMapper json = JsonMapperFactory.create();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AtomicBoolean oracleDown = new AtomicBoolean(false);
    private ApiServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        Files.writeString(frontend.resolve("index.html"), "<h1>RutaVida de prueba</h1>", StandardCharsets.UTF_8);
        EmergencyTypeDAO types = new EmergencyTypeDAO() {
            @Override
            public Optional<EmergencyType> findById(long id) {
                return TYPES.stream().filter(type -> type.getId() == id).findFirst();
            }

            @Override
            public List<EmergencyType> findAll() {
                if (oracleDown.get()) {
                    throw new DataAccessException("ORA-12541: TNS:no listener", null);
                }
                return TYPES;
            }
        };
        WebTestSupport support = new WebTestSupport();
        server = new ApiServer(support.sessions(), new CatalogController(new CatalogService(types)), frontend);
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
        JsonNode types = json.readTree(allowed.body()).get("value");
        assertEquals(2, types.size());
        assertEquals("EMERGENCIA_MEDICA", types.get(0).get("name").asText());
        assertEquals(9, types.get(0).get("priority").asInt());
    }

    @Test
    void sessionAndLogout() throws Exception {
        String token = login("ana.perez");

        HttpResponse<String> session = send("GET", "/api/auth/session", token, null);
        assertEquals(200, session.statusCode());
        JsonNode value = json.readTree(session.body()).get("value");
        assertTrue(value.get("token").isNull(), "the session does not repeat the token");
        assertEquals("ana.perez", value.get("user").get("username").asText());

        assertEquals(200, send("POST", "/api/auth/logout", token, null).statusCode());
        assertEquals(401, send("GET", "/api/auth/session", token, null).statusCode());
        assertEquals(401, send("GET", "/api/emergency-types", token, null).statusCode());
    }

    @Test
    void oracleDownIs503WithoutTechnicalDetails() throws Exception {
        String token = login("ana.perez");
        oracleDown.set(true);

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
}