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
import java.util.function.ToLongFunction;

/**
 * Native Javalin WebSocket for live-map viewers. The browser authenticates with its existing
 * bearer token as the first frame; location publishing itself remains a protected REST call.
 */
public final class GpsHub {

    private record LoginFrame(String type, String token, String audience) { }
    private record DriverSocket(WsContext context, String token, long vehicleId) { }
    private record ViewerSocket(WsContext context, String token, boolean coordinator) { }

    private final WebSessionController sessions;
    private final ObjectMapper json = JsonMapperFactory.create();
    private final Map<String, ViewerSocket> viewers = new ConcurrentHashMap<>();
    private final Map<String, DriverSocket> drivers = new ConcurrentHashMap<>();
    private final ToLongFunction<UserSession> vehicleOfDriver;

    public GpsHub(WebSessionController sessions, ToLongFunction<UserSession> vehicleOfDriver) {
        this.sessions = sessions;
        this.vehicleOfDriver = vehicleOfDriver;
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
            if ("driver".equals(frame.audience())) {
                long vehicleId;
                try {
                    vehicleId = vehicleOfDriver.applyAsLong(authenticated.value());
                } catch (RuntimeException denied) {
                    context.closeSession(1008, "Sin unidad GPS asignada");
                    return;
                }
                viewers.remove(context.sessionId());
                drivers.put(context.sessionId(), new DriverSocket(context, frame.token(), vehicleId));
                send(context, Map.of("type", "ready"));
                return;
            }
            OperationResult<Void> permission = sessions.authorize(authenticated.value(), "EMERGENCIAS", "CONSULTAR");
            if (!permission.success()) {
                context.closeSession(1008, "Sin permiso para ver el mapa");
                return;
            }
            drivers.remove(context.sessionId());
            boolean coordinator = sessions.authorize(authenticated.value(), "DESPACHO", "GESTIONAR").success();
            viewers.put(context.sessionId(), new ViewerSocket(context, frame.token(), coordinator));
            send(context, Map.of("type", "ready"));
        } catch (JsonProcessingException ignored) {
            context.closeSession(1007, "Mensaje no válido");
        }
    }

    public void close(WsCloseContext context) {
        viewers.remove(context.sessionId());
        drivers.remove(context.sessionId());
    }

    /** Pushes the confirmed database position to every authorized map without a page refresh. */
    public void broadcast(GpsPosition position) {
        Map<String, Object> event = Map.of("type", "position", "position", position);
        for (ViewerSocket viewer : viewers.values()) {
            try {
                send(viewer.context(), event);
            } catch (RuntimeException ignored) {
                viewers.remove(viewer.context().sessionId(), viewer);
            }
        }
    }

    /** Tells viewers to refresh mission state immediately after arrival or finalization. */
    public void broadcastMission(long vehicleId) {
        broadcastViewers(Map.of("type", "mission", "vehicleId", vehicleId));
        for (DriverSocket driver : drivers.values()) {
            if (driver.vehicleId() != vehicleId) continue;
            try {
                var session = sessions.authenticate("Bearer " + driver.token(), false);
                if (!session.success() || vehicleOfDriver.applyAsLong(session.value()) != vehicleId) {
                    driver.context().closeSession(1008, "Sesión o unidad no válida");
                    drivers.remove(driver.context().sessionId(), driver);
                    continue;
                }
                send(driver.context(), Map.of("type", "mission"));
            } catch (RuntimeException denied) {
                drivers.remove(driver.context().sessionId(), driver);
            }
        }
    }

    /** Staff refresh the incident list even when no unit could be dispatched. */
    public void broadcastPanic() {
        broadcastViewers(Map.of("type", "panic"));
    }

    private void broadcastViewers(Map<String, Object> event) {
        for (ViewerSocket viewer : viewers.values()) {
            if (!viewer.coordinator()) continue;
            try {
                var authenticated = sessions.authenticate("Bearer " + viewer.token(), false);
                if (!authenticated.success()
                        || !sessions.authorize(authenticated.value(), "DESPACHO", "GESTIONAR").success()) {
                    viewer.context().closeSession(1008, "Sesión de coordinación no válida");
                    viewers.remove(viewer.context().sessionId(), viewer);
                    continue;
                }
                send(viewer.context(), event);
            } catch (RuntimeException ignored) {
                viewers.remove(viewer.context().sessionId(), viewer);
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
