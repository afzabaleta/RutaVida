// Live map D7: positions come from the server, not the browser.
import { ApiError, apiWebSocketUrl, getTracking, gpsSocketToken, hasPermission, listMissionRoutes } from "./api-client.js";
import { UNIT_Z, unitIcon, unitSlots, vehicleLabel } from "./map-icons.js";

const REFRESH_MS = 5000;
const SLIDE_MS = 1200;
const ACTIVE_ROUTE = { color: "#2f7bff", weight: 6, opacity: 0.85 };
const REDUCED_MOTION = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;

function slide(marker, target) {
  const from = marker.getLatLng();

  if (REDUCED_MOTION || (from.lat === target[0] && from.lng === target[1])) {
    cancelAnimationFrame(marker._rvFrame);
    marker.setLatLng(target);
    return;
  }

  const start = performance.now();
  const step = (now) => {
    const t = Math.min(1, (now - start) / SLIDE_MS);
    marker.setLatLng([
      from.lat + (target[0] - from.lat) * t,
      from.lng + (target[1] - from.lng) * t,
    ]);
    if (t < 1) {
      marker._rvFrame = requestAnimationFrame(step);
    }
  };

  cancelAnimationFrame(marker._rvFrame);
  marker._rvFrame = requestAnimationFrame(step);
}

function unitPopup(unit) {
  const box = document.createElement("div");
  const title = document.createElement("strong");
  title.textContent = unit.unitCode;
  title.style.display = "block";

  const detail = document.createElement("p");
  detail.textContent = `${vehicleLabel(unit.vehicleType)} · ${unit.operationalStatus}`;

  const source = document.createElement("small");
  const updated = unit.updatedAt ? new Date(unit.updatedAt).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "sin hora";
  const telemetry = [
    Number.isFinite(unit.speedKmh) ? `${Math.round(unit.speedKmh)} km/h` : null,
    Number.isFinite(unit.headingDegrees) ? `${Math.round(unit.headingDegrees)}°` : null,
  ].filter(Boolean).join(" · ");
  source.textContent = `${unit.example ? "EJEMPLO · " : ""}${unit.source ?? "sin datos"} · ${updated}${telemetry ? ` · ${telemetry}` : ""}`;

  box.append(title, detail, source);
  return box;
}

export function startLiveMap(map, session, { onSnapshot, onError, onRouteChanged, onConnection } = {}) {
  if (!map || !hasPermission(session, "EMERGENCIAS:CONSULTAR")) {
    return null;
  }

  const canSeeRoutes = hasPermission(session, "RUTAS:CONSULTAR");
  const markers = new Map();
  const iconKeys = new Map();
  const lines = new Map();
  const routeLayer = L.layerGroup().addTo(map);
  const destinations = new Map();

  let timer = null;
  let stopped = false;
  let refreshing = false;
  let socket = null;
  let reconnectTimer = null;
  let lastSnapshot = null;

  function drawUnits(units) {
    const slots = unitSlots(units);
    const seen = new Set();

    units.forEach((unit, index) => {
      if (!unit.location) return;

      seen.add(unit.vehicleId);
      const key = `${unit.vehicleType}|${unit.operationalStatus}|${slots[index]}`;
      let marker = markers.get(unit.vehicleId);

      if (!marker) {
        marker = L.marker(unit.location, {
          icon: unitIcon(unit.vehicleType, unit.operationalStatus, slots[index]),
          keyboard: false,
          zIndexOffset: UNIT_Z,
        }).addTo(map);
        markers.set(unit.vehicleId, marker);
      } else {
        slide(marker, unit.location);
        if (iconKeys.get(unit.vehicleId) !== key) {
          marker.setIcon(unitIcon(unit.vehicleType, unit.operationalStatus, slots[index]));
        }
      }

      iconKeys.set(unit.vehicleId, key);
      marker.bindPopup(unitPopup(unit));
    });

    for (const [vehicleId, marker] of markers) {
      if (!seen.has(vehicleId)) {
        cancelAnimationFrame(marker._rvFrame);
        marker.remove();
        markers.delete(vehicleId);
        iconKeys.delete(vehicleId);
      }
    }
  }

  async function drawMissionRoutes(missions) {
    const open = new Set(missions.map((mission) => mission.assignmentId));

    for (const [assignmentId, drawn] of lines) {
      if (!open.has(assignmentId)) {
        drawn.line?.remove();
        destinations.get(assignmentId)?.remove();
        destinations.delete(assignmentId);
        lines.delete(assignmentId);
      }
    }

    if (!canSeeRoutes) return;

    for (const mission of missions) {
      const drawn = lines.get(mission.assignmentId);

      if (mission.activeRouteId == null) {
        drawn?.line?.remove();
        destinations.get(mission.assignmentId)?.remove();
        destinations.delete(mission.assignmentId);
        lines.set(mission.assignmentId, {
          version: mission.routesVersion,
          line: null,
          route: null,
        });

        const previousDestination = destinations.get(mission.assignmentId);
        if (mission.destination) {
          const text = [
            `Emergencia #${mission.emergencyId}`,
            Number.isFinite(mission.remainingDistanceKm) ? `Restan ${mission.remainingDistanceKm.toFixed(2)} km` : null,
            Number.isFinite(mission.etaMinutes) ? `ETA ${Math.ceil(mission.etaMinutes)} min` : null,
          ].filter(Boolean).join(" · ");
          if (previousDestination) previousDestination.setLatLng(mission.destination).bindTooltip(text);
          else destinations.set(mission.assignmentId, L.circleMarker(mission.destination, {
            radius: 10, color: "#e91e3c", weight: 3, fillColor: "#ff5a70", fillOpacity: 0.85,
          }).bindTooltip(text, { direction: "top" }).addTo(routeLayer));
        }
        continue;
      }

      if (drawn && drawn.version === mission.routesVersion && drawn.line) {
        const destination = destinations.get(mission.assignmentId);
        if (destination && mission.destination) {
          const text = [
            `Emergencia #${mission.emergencyId}`,
            Number.isFinite(mission.remainingDistanceKm) ? `Restan ${mission.remainingDistanceKm.toFixed(2)} km` : null,
            Number.isFinite(mission.etaMinutes) ? `ETA ${Math.ceil(mission.etaMinutes)} min` : null,
          ].filter(Boolean).join(" · ");
          destination.setLatLng(mission.destination).bindTooltip(text);
        }
        continue;
      }

      try {
        const routes = await listMissionRoutes(mission.assignmentId);
        const active = routes.find((route) => route.status === "ACTIVA");

        drawn?.line?.remove();

        const line = active
          ? L.polyline(active.path, {
              ...ACTIVE_ROUTE,
              interactive: false,
            }).addTo(routeLayer)
          : null;

        lines.set(mission.assignmentId, {
          version: mission.routesVersion,
          line,
          route: active ?? null,
        });

        // D12: a mission that already had a route and got a new one was recalculated.
        if (drawn?.route && drawn.version !== mission.routesVersion && active) {
          onRouteChanged?.(mission, active);
        }
      } catch (error) {
        console.warn("No se pudo leer la ruta de la misión", mission.assignmentId, error);
      }
    }
  }

  async function refresh() {
    if (stopped || refreshing || document.visibilityState !== "visible") {
      return;
    }

    refreshing = true;
    clearTimeout(timer);

    try {
      const snapshot = await getTracking();
      if (stopped || document.visibilityState !== "visible") return;

      lastSnapshot = snapshot;
      drawUnits(snapshot.units);
      await drawMissionRoutes(snapshot.missions);

      if (stopped || document.visibilityState !== "visible") return;

      const routes = new Map(
        [...lines].map(([id, drawn]) => [id, drawn.route])
      );

      onSnapshot?.(snapshot, routes);
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        stopped = true;
      }
      onError?.(error);
    } finally {
      refreshing = false;
      schedule();
    }
  }

  function schedule() {
    clearTimeout(timer);
    if (!stopped && document.visibilityState === "visible") {
      timer = setTimeout(refresh, REFRESH_MS);
    }
  }

  function applyGpsPosition(position) {
    if (!lastSnapshot) {
      refresh();
      return;
    }
    const unit = lastSnapshot.units.find((item) => item.vehicleId === position.vehicleId);
    if (!unit || !position.location) {
      refresh();
      return;
    }
    unit.location = [position.location.latitude, position.location.longitude];
    unit.updatedAt = position.updatedAt;
    unit.speedKmh = position.speedKmh;
    unit.headingDegrees = position.headingDegrees;
    unit.source = position.source;
    drawUnits(lastSnapshot.units);
    if (position.routeRecalculated) {
      refresh();
    }
    onSnapshot?.(lastSnapshot, new Map([...lines].map(([id, drawn]) => [id, drawn.route])));
  }

  function connectGpsSocket() {
    const token = gpsSocketToken();
    if (stopped || !token || !window.WebSocket) return;
    clearTimeout(reconnectTimer);
    socket = new WebSocket(apiWebSocketUrl("/ws/gps"));
    onConnection?.("Conectando seguimiento en tiempo real…", "connecting");
    socket.addEventListener("open", () => socket?.send(JSON.stringify({ type: "auth", token })));
    socket.addEventListener("message", (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === "ready") onConnection?.("Seguimiento en tiempo real conectado", "online");
        if (data.type === "position") applyGpsPosition(data.position);
      } catch {
        // A malformed socket frame never interrupts the five-second REST fallback.
      }
    });
    socket.addEventListener("close", () => {
      socket = null;
      if (!stopped) {
        onConnection?.("Seguimiento en tiempo real reconectando…", "offline");
        reconnectTimer = setTimeout(connectGpsSocket, REFRESH_MS);
      }
    });
    socket.addEventListener("error", () => socket?.close());
  }

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      refresh();
    } else {
      clearTimeout(timer);
    }
  });

  refresh();
  connectGpsSocket();

  return {
    focus(vehicleId) {
      const marker = markers.get(vehicleId);
      if (!marker) return false;

      map.setView(marker.getLatLng(), Math.max(map.getZoom(), 15));
      marker.openPopup();
      return true;
    },

    showRoute(assignmentId) {
      const line = lines.get(assignmentId)?.line;
      if (line) {
        map.fitBounds(line.getBounds(), { padding: [30, 30] });
      }
    },

    stop() {
      stopped = true;
      clearTimeout(timer);
      clearTimeout(reconnectTimer);
      socket?.close();
    },
  };
}
