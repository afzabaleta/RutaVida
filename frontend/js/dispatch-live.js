// Live follow-up of the open mission on the dispatch page: every 5 s it asks /api/tracking
// (docs/api.md 8.1) where the unit is and in which status the mission is. The unit moves on
// the map, and when the simulation changes the mission (it arrived EN_SITIO, the route was
// recalculated, it ended) the page reads it again by itself. Positions are SIMULACION.
// /api/tracking is an automatic query: it does not keep the session alive (DT-08).
import { getTracking, hasPermission } from "./api-client.js";
import { iconMarker, UNIT_Z, unitIcon } from "./map-icons.js";

const LIVE_MS = 5000;
const TIME = new Intl.DateTimeFormat("es-CO", { hour: "numeric", minute: "2-digit", second: "2-digit" });

/**
 * Starts the follow-up. Returns null without a map or without EMERGENCIAS:CONSULTAR (the
 * permission of /api/tracking): then the page works as before, with «Actualizar estado».
 *
 * status:   element that says "En vivo · SIMULACION · hh:mm:ss".
 * isBusy:   true while the page is saving something; then no change is reported.
 * onChange: (kind, trackedMission) with kind "status", "route" or "ended".
 */
export function startDispatchLive(map, session, { status, isBusy, onChange }) {
  if (!map || !hasPermission(session, "EMERGENCIAS:CONSULTAR")) {
    return null;
  }

  const layer = L.layerGroup().addTo(map);
  const idleText = status?.textContent.trim() ?? "";
  let followed = null;
  let marker = null;
  let markerState = "";
  let timer = null;
  let asking = false;

  function say(text) {
    if (!status) return;
    status.replaceChildren(document.createElement("span"), ` ${text}`);
  }

  function drawUnit(unit) {
    if (!unit?.location) return;
    const state = `${unit.vehicleType}|${unit.operationalStatus}`;
    const text = `${unit.unitCode} · ${unit.operationalStatus} · posición ${unit.source ?? "SIMULACION"}`;
    if (!marker) {
      marker = iconMarker(map, unit.location, unitIcon(unit.vehicleType, unit.operationalStatus), text, UNIT_Z);
      layer.addLayer(marker);
    } else {
      marker.setLatLng(unit.location);
      marker.setPopupContent(text);
      if (state !== markerState) {
        marker.setIcon(unitIcon(unit.vehicleType, unit.operationalStatus));
      }
    }
    markerState = state;
  }

  async function tick() {
    if (!followed || asking || document.visibilityState !== "visible") return;
    asking = true;
    try {
      const snapshot = await getTracking();
      if (!followed) return;
      drawUnit(snapshot.units.find((unit) => unit.vehicleId === followed.vehicleId));
      say(`En vivo · SIMULACION · ${TIME.format(new Date())}`);

      // While the coordinator is saving, the page itself changes the mission: wait.
      if (isBusy?.()) return;

      const tracked = snapshot.missions.find((item) => item.assignmentId === followed.id);
      let kind = null;
      if (!tracked) {
        // Reported once: if the page finds the mission still open, it is not asked again.
        kind = followed.missing ? null : "ended";
        followed.missing = true;
      } else if (tracked.status !== followed.status) {
        kind = "status";
      } else if (followed.version !== null && tracked.routesVersion !== followed.version) {
        kind = "route";
      }
      followed.version = tracked?.routesVersion ?? followed.version;
      if (kind) {
        followed.status = tracked?.status ?? null;
        await onChange?.(kind, tracked ?? null);
      }
    } catch (error) {
      say("Seguimiento no disponible");
      console.warn("No se pudo leer el seguimiento", error);
    } finally {
      asking = false;
    }
  }

  function stop() {
    followed = null;
    clearInterval(timer);
    timer = null;
    layer.clearLayers();
    marker = null;
    markerState = "";
    if (status) status.textContent = idleText;
  }

  return {
    /** Follows the open mission of the emergency on screen; null stops the follow-up. */
    follow(mission) {
      if (!mission || ["FINALIZADA", "CANCELADA"].includes(mission.status)) {
        stop();
        return;
      }
      const same = followed?.id === mission.id;
      // A change made on this page (the transfer saves new routes) is the new starting point,
      // not a recalculation: the version is read again on the next query.
      const keepVersion = same && followed.status === mission.status;
      followed = {
        id: mission.id,
        vehicleId: mission.vehicleId,
        status: mission.status,
        version: keepVersion ? followed.version : null,
        missing: same ? followed.missing : false,
      };
      if (!same) {
        layer.clearLayers();
        marker = null;
      }
      if (!timer) {
        timer = setInterval(tick, LIVE_MS);
      }
      tick();
    },
    stop,
  };
}
