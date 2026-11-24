package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.controller.GpsController;
import co.edu.unicesar.rutavida.controller.WebSessionController;
import co.edu.unicesar.rutavida.dto.GpsPosition;
import co.edu.unicesar.rutavida.dto.OperationResult;
import co.edu.unicesar.rutavida.model.UserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Javalin WebSocket for live-map viewers. The browser authenticates with its existing
 * bearer token as the first frame; location publishing itself remains a protected REST call.
 */
public final class GpsHub {

    private record LoginFrame(String type, String token) { }

    private final WebSessionController sessions;
    private final ObjectMapper json = JsonMapperFactory.create();
    private final Map<String, WsContext> viewers = new ConcurrentHashMap<>();

    public GpsHub(WebSessionController sessions) {
        this.sessions = sessions;
    }

    public void message(WsMessageContext context) {
        try {
            LoginFrame frame = json.readValue(context.message(), LoginFrame.class);
            if (frame == null || !"auth".equals(frame.type()) || frame.token() == null) {
                context.closeSession(1008, "Autenticación requerida");
                return;
            }
            OperationResult<UserSession> authenticated = sessions.authenticate("Bearer " + frame.token(), false);
            if (!authenticated.success()) {
                context.closeSession(1008, "Sesión no válida");
                return;
            }
            OperationResult<Void> permission = sessions.authorize(authenticated.value(), "EMERGENCIAS", "CONSULTAR");
            if (!permission.success()) {
                context.closeSession(1008, "Sin permiso para ver el mapa");
                return;
            }
            viewers.put(context.sessionId(), context);
            send(context, Map.of("type", "ready"));
        } catch (JsonProcessingException ignored) {
            context.closeSession(1007, "Mensaje no válido");
        }
    }

    public void close(WsCloseContext context) {
        viewers.remove(context.sessionId());
    }

    /** Pushes the confirmed database position to every authorized map without a page refresh. */
    public void broadcast(GpsPosition position) {
        Map<String, Object> event = Map.of("type", "position", "position", position);
        for (WsContext viewer : viewers.values()) {
            try {
                send(viewer, event);
            } catch (RuntimeException ignored) {
                viewers.remove(viewer.sessionId(), viewer);
            }
        }
    }

    private void send(WsContext context, Object payload) {
        try {
            context.send(json.writeValueAsString(payload));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo serializar el evento GPS.", error);
        }
    }
}
