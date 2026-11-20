// Live follow-up of the open mission on the dispatch page (D13).
// Every 5 s it asks /api/tracking for simulated positions and mission changes.
// Automatic tracking does not keep the session alive (DT-08).

import { getTracking, hasPermission } from "./api-client.js";
import { iconMarker, UNIT_Z, unitIcon } from "./map-icons.js";

const LIVE_MS = 5000;

const TIME = new Intl.DateTimeFormat("es-CO", {
  hour: "numeric",
  minute: "2-digit",
  second: "2-digit",
});

/**
 * Starts the follow-up of the open mission.
 * Without permission, the page keeps its manual refresh.
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

    status.replaceChildren(
      document.createElement("span"),
      ` ${text}`
    );
  }

  function drawUnit(unit) {
    if (!unit?.location) return;

    const state = `${unit.vehicleType}|${unit.operationalStatus}`;
    const text = `${unit.unitCode} · ${unit.operationalStatus} · posición ${unit.source ?? "SIMULACION"}`;

    if (!marker) {
      marker = iconMarker(
        map,
        unit.location,
        unitIcon(unit.vehicleType, unit.operationalStatus),
        text,
        UNIT_Z
      );

      layer.addLayer(marker);
    } else {
      marker.setLatLng(unit.location);
      marker.setPopupContent(text);

      if (state !== markerState) {
        marker.setIcon(
          unitIcon(unit.vehicleType, unit.operationalStatus)
        );
      }
    }

    markerState = state;
  }

  async function tick() {
    if (!followed || asking || document.visibilityState !== "visible") {
      return;
    }

    asking = true;
    const target = followed;

    try {
      const snapshot = await getTracking();

      // Ignore responses from a mission that is no longer followed.
      if (followed !== target || document.visibilityState !== "visible") {
        return;
      }

      drawUnit(
        snapshot.units.find(
          (unit) => unit.vehicleId === followed.vehicleId
        )
      );

      say(`En vivo · SIMULACION · ${TIME.format(new Date())}`);

      // Do not report changes while the coordinator is saving.
      if (isBusy?.()) return;

      const tracked = snapshot.missions.find(
        (item) => item.assignmentId === followed.id
      );

      let kind = null;

      if (!tracked) {
        kind = followed.missing ? null : "ended";
        followed.missing = true;
      } else if (tracked.status !== followed.status) {
        kind = "status";
      } else if (
        followed.version !== null &&
        tracked.routesVersion !== followed.version
      ) {
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
    /** Follows the mission on screen; null stops the follow-up. */
    follow(mission) {
      if (
        !mission ||
        ["FINALIZADA", "CANCELADA"].includes(mission.status)
      ) {
        stop();
        return;
      }

      const same = followed?.id === mission.id;

      // A status change made on this page is the new baseline,
      // not a recalculation caused by traffic.
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
        markerState = "";
      }

      if (!timer) {
        timer = setInterval(tick, LIVE_MS);
      }

      tick();
    },

    stop,
  };
}