// D12: simulation control and traffic layer of the main panel (docs/api.md 8.2 and 10).
// Everything here is SIMULACION: there is no real GPS nor real traffic source.

import {
  forceTraffic,
  getSimulation,
  getTracking,
  hasPermission,
  listMissionRoutes,
  listTrafficSegments,
  startSimulation,
  stopSimulation,
} from "./api-client.js";

import { showSimulationState } from "./app-shell.js";

const STATE_MS = 5000;
const TRAFFIC_MS = 15000;
const TOAST_MS = 10000;

const TRAFFIC_STYLE = {
  MODERADO: { color: "#f59e0b", weight: 6, opacity: 0.9 },
  CONGESTIONADO: { color: "#e11d48", weight: 7, opacity: 0.95 },
};

const LEVEL_TEXT = {
  MODERADO: "Tráfico moderado",
  CONGESTIONADO: "Tráfico congestionado",
};

/**
 * Starts the simulation panel. Returns null when the user cannot see the traffic.
 */
export function startSimulationPanel(map, session, elements) {
  if (!map || !hasPermission(session, "TRAFICO:CONSULTAR")) {
    return null;
  }

  const {
    panel,
    stateText,
    startButton,
    stopButton,
    congestButton,
    releaseButton,
    message,
    liveStatus,
    toast,
  } = elements;

  const canManage = hasPermission(session, "TRAFICO:GESTIONAR");
  const canCongest = canManage && hasPermission(session, "RUTAS:CONSULTAR");
  const trafficLayer = L.layerGroup().addTo(map);
  const forced = new Set();

  let running = null;
  let busy = false;
  let toastTimer = null;
  let currentState = null;

  panel.hidden = false;
  startButton.hidden = !canManage;
  stopButton.hidden = !canManage;
  congestButton.hidden = !canCongest;
  releaseButton.hidden = !canCongest;

  startButton.disabled = true;
  stopButton.disabled = true;
  congestButton.disabled = true;
  releaseButton.disabled = true;

  function say(text, error = false) {
    message.textContent = text;
    message.classList.toggle("is-error", error);
  }

  function paintState(state) {
    currentState = state;
    running = Boolean(state?.running);

    panel.classList.toggle("is-running", running);

    stateText.textContent = running
      ? `En marcha · cada ${state.tickSeconds} s · ${state.source}`
      : "Detenida: las unidades no se mueven";

    startButton.disabled = busy || running;
    stopButton.disabled = busy || !running;
    congestButton.disabled = busy || !running;
    releaseButton.disabled = busy || forced.size === 0;

    showSimulationState(running);

    if (liveStatus) {
      liveStatus.textContent = running
        ? "Seguimiento activo · simulación en marcha"
        : "Seguimiento activo · simulación detenida";
    }
  }

  async function refreshState() {
    try {
      paintState(await getSimulation());
    } catch (error) {
      stateText.textContent = "Estado no disponible";
      say(error.message, true);
    }
  }

  function drawTraffic(segments) {
    trafficLayer.clearLayers();

    for (const segment of segments) {
      if (!segment.from || !segment.to) continue;

      const line = L.polyline([segment.from, segment.to], {
        ...(TRAFFIC_STYLE[segment.level] ?? TRAFFIC_STYLE.MODERADO),
        lineCap: "round",
      });

      const popup = document.createElement("div");

      const street = document.createElement("strong");
      street.textContent = segment.streetName || "Vía sin nombre";
      street.style.display = "block";

      const detail = document.createElement("span");
      detail.textContent =
        `${LEVEL_TEXT[segment.level] ?? segment.level} · ×${segment.congestionFactor}`;

      const source = document.createElement("small");
      source.textContent = ` · ${segment.source}`;

      popup.append(street, detail, source);
      line.bindPopup(popup).addTo(trafficLayer);
    }
  }

  async function refreshTraffic() {
    try {
      drawTraffic(await listTrafficSegments());
    } catch (error) {
      console.warn("No se pudo leer la capa de tráfico", error);
    }
  }

  /** Runs one button action: the buttons stay disabled until it ends. */
  async function act(work) {
    if (busy || !currentState) return;

    busy = true;
    paintState(currentState);

    try {
      await work();
    } catch (error) {
      say(error.message, true);
    } finally {
      busy = false;
      paintState(currentState);
      await refreshState();
    }
  }

  /** The newest mission on its way and the middle third of its ACTIVA route. */
  async function middleOfActiveRoute() {
    const snapshot = await getTracking();

    const mission = (snapshot.missions ?? [])
      .filter((item) =>
        item.status === "EN_RUTA" ||
        item.status === "ASIGNADA"
      )
      .sort((a, b) => b.assignmentId - a.assignmentId)[0];

    if (!mission) {
      throw new Error(
        "No hay ninguna unidad en camino. Despacha una unidad primero."
      );
    }

    const routes = await listMissionRoutes(mission.assignmentId);
    const active = routes.find((route) => route.status === "ACTIVA");
    const ids = active?.segmentIds ?? [];

    if (ids.length < 3) {
      throw new Error(
        "La ruta activa es muy corta para mostrar un recálculo."
      );
    }

    return {
      mission,
      ids: ids.slice(
        Math.floor(ids.length / 3),
        Math.ceil((ids.length * 2) / 3)
      ),
    };
  }

  startButton.addEventListener("click", () => act(async () => {
    paintState(await startSimulation());

    say(
      "Simulación iniciada. Las unidades con misión empiezan a moverse."
    );
  }));

  stopButton.addEventListener("click", () => act(async () => {
    paintState(await stopSimulation());

    say(
      "Simulación detenida. Las unidades se quedan donde están."
    );
  }));

  congestButton.addEventListener("click", () => act(async () => {
    const { mission, ids } = await middleOfActiveRoute();

    for (const id of ids) {
      await forceTraffic(id, "CONGESTIONADO");
      forced.add(id);
    }

    say(
      `Congestión SIMULADA en ${ids.length} tramos de la ruta de la misión ${mission.assignmentId}. `
      + "El sistema la revisa en el siguiente ciclo."
    );

    setTimeout(refreshTraffic, 3000);
  }));

  releaseButton.addEventListener("click", () => act(async () => {
    const ids = [...forced];

    for (const id of ids) {
      await forceTraffic(id, "FLUIDO");
      forced.delete(id);
    }

    say(`Tráfico liberado en ${ids.length} tramos.`);

    setTimeout(refreshTraffic, 3000);
  }));

  function every(work, ms) {
    work();

    setInterval(() => {
      if (document.visibilityState === "visible") {
        work();
      }
    }, ms);
  }

  every(refreshState, STATE_MS);
  every(refreshTraffic, TRAFFIC_MS);

  return {
    /** The live map saw a new ACTIVA route: tell the operator. */
    routeChanged(text) {
      if (!toast) return;

      toast.textContent = text;
      toast.hidden = false;

      clearTimeout(toastTimer);

      toastTimer = setTimeout(() => {
        toast.hidden = true;
      }, TOAST_MS);

      refreshTraffic();
    },
  };
}
