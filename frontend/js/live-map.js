// Live map D7: positions come from the server, not the browser.
import { ApiError, getTracking, hasPermission, listMissionRoutes } from "./api-client.js";
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
  source.textContent = `${unit.example ? "EJEMPLO · " : ""}Posición ${unit.source ?? "sin datos"}`;

  box.append(title, detail, source);
  return box;
}

export function startLiveMap(map, session, { onSnapshot, onError } = {}) {
  if (!map || !hasPermission(session, "EMERGENCIAS:CONSULTAR")) {
    return null;
  }

  const canSeeRoutes = hasPermission(session, "RUTAS:CONSULTAR");
  const markers = new Map();
  const iconKeys = new Map();
  const lines = new Map();
  const routeLayer = L.layerGroup().addTo(map);

  let timer = null;
  let stopped = false;
  let refreshing = false;

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
        lines.delete(assignmentId);
      }
    }

    if (!canSeeRoutes) return;

    for (const mission of missions) {
      const drawn = lines.get(mission.assignmentId);

      if (mission.activeRouteId == null) {
        drawn?.line?.remove();
        lines.set(mission.assignmentId, {
          version: mission.routesVersion,
          line: null,
          route: null,
        });
        continue;
      }

      if (drawn && drawn.version === mission.routesVersion && drawn.line) {
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

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      refresh();
    } else {
      clearTimeout(timer);
    }
  });

  refresh();

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
  };
}