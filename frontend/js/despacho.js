// Dispatch page (CU-08, CU-09, CU-10 and CU-12). The units that can go come ordered by the time
// the route engine calculates over the streets (6.1); the chosen unit shows its 3 routes without
// saving anything (6.2); the dispatch is one transaction in the server (6.3); and then the page
// follows the mission: its routes (6.4), its status (6.6) and the closing of the emergency (6.7).

import {
  changeMissionStatus,
  closeEmergency,
  dispatchUnit,
  findEmergency,
  findOpenAssignment,
  hasPermission,
  listCandidates,
  listEmergencies,
  listMissionRoutes,
  listUnits,
  previewRoutes,
  typeLabel,
} from "./api-client.js";

import { startPage } from "./page-session.js";

import {
  byId,
  addText,
  makeTag,
  createBaseMap,
} from "./map-base.js";

import { iconMarker, placeIcon, UNIT_Z, unitIcon, unitSlots } from "./map-icons.js";

import { drawRoutes } from "./dispatch-routes.js";

const MINUTES = new Intl.NumberFormat("es-CO", { maximumFractionDigits: 1 });
const KM = new Intl.NumberFormat("es-CO", { maximumFractionDigits: 2 });

/** What the coordinator can do with the mission in each status (DispatchService decides in the end). */
const MISSION_ACTIONS = {
  ASIGNADA: [
    { status: "EN_RUTA", text: "La unidad salió (EN_RUTA)" },
    { status: "CANCELADA", text: "Cancelar asignación" },
  ],
  EN_RUTA: [
    { status: "EN_SITIO", text: "Llegó al sitio (EN_SITIO)" },
    { status: "CANCELADA", text: "Cancelar asignación" },
  ],
};

/** Emergencies that can be closed (DispatchService.CLOSABLE): REPORTADA only as a false alarm. */
const CLOSABLE = ["REPORTADA", "EN_SITIO", "TRASLADANDO"];

let session = null;
let map = null;
let markerLayer = null;
let routeLayer = null;
let current = null;
let candidates = [];
let selected = null;
let mission = null;
let busy = false;
let confirmingClose = false;
const unitCodes = new Map();

const canDispatch = () => hasPermission(session, "DESPACHO:GESTIONAR");
const canPreview = () => hasPermission(session, "RUTAS:GESTIONAR");

function showMessage(text, isError = false) {
  const message = byId("dispatch-message");
  message.textContent = text;
  message.className = isError ? "form-message error" : "form-message ok";
  message.hidden = false;
}

function hideMessage() {
  byId("dispatch-message").hidden = true;
}

function setRouteTag(text, isReal) {
  const tag = byId("route-tag");
  tag.textContent = text;
  tag.className = isReal ? "data-tag real-tag" : "data-tag";
}

function clearRoutes(text) {
  routeLayer?.clearLayers();
  const list = byId("route-list");
  list.replaceChildren();
  addText(list, "p", text, "empty-state");
  byId("route-note").textContent = "";
  setRouteTag("SIN RUTAS", false);
}

function unitName(vehicleId) {
  return unitCodes.get(vehicleId) ?? `Unidad ${vehicleId}`;
}

/** The unit code of a mission opened before this page: from GET /api/units, if the role can. */
async function learnUnitCode(vehicleId) {
  if (unitCodes.has(vehicleId)) {
    return;
  }
  try {
    for (const unit of await listUnits()) {
      unitCodes.set(unit.id, unit.code);
    }
  } catch {
    // Without UNIDADES:CONSULTAR the mission shows "Unidad 7".
  }
}

function renderEmergency() {
  const box = byId("emergency-detail");
  box.replaceChildren();

  const card = document.createElement("article");
  card.className = "data-card";

  const top = document.createElement("div");
  top.className = "data-card-top";
  addText(top, "h3", `${current.code} · ${typeLabel(current.type)}`);
  top.appendChild(makeTag("ORACLE", true));
  card.appendChild(top);

  addText(card, "p", current.place);
  addText(card, "p", `Severidad ${current.severity} · Estado ${current.status}`, "status-text");
  box.appendChild(card);

  const option = byId("emergency-select").querySelector(`option[value="${current.id}"]`);
  if (option) {
    option.textContent = `${current.code} · ${typeLabel(current.type)} · ${current.status}`;
  }
}

function drawMarkers() {
  if (!markerLayer) {
    return;
  }
  markerLayer.clearLayers();

  markerLayer.addLayer(
    iconMarker(map, current.location, placeIcon("emergency"), `Emergencia ${current.code}`),
  );

  const slots = unitSlots(candidates);
  for (const [index, unit] of candidates.entries()) {
    if (unit.location) {
      markerLayer.addLayer(
        iconMarker(map, unit.location, unitIcon(unit.vehicleType, "DISPONIBLE", slots[index]),
          `${unit.unitCode} · ${MINUTES.format(unit.etaMinutes)} min${unit.example ? " (EJEMPLO)" : ""}`, UNIT_Z),
      );
    }
  }

  map.setView(current.location, 15);
}

function renderCandidates() {
  const list = byId("unit-list");
  list.replaceChildren();
  byId("unit-pill").textContent = String(candidates.length);

  if (mission) {
    addText(list, "p", `${unitName(mission.vehicleId)} ya atiende esta emergencia.`, "empty-state");
    return;
  }
  if (current.status !== "REPORTADA") {
    addText(list, "p", `La emergencia está ${current.status}: solo se despacha una emergencia REPORTADA.`,
      "empty-state");
    return;
  }
  if (!canDispatch()) {
    addText(list, "p", "Tu rol puede consultar el despacho, pero no despachar unidades.", "empty-state");
    return;
  }
  if (candidates.length === 0) {
    addText(list, "p", "No hay unidades disponibles que lleguen por la red vial cargada.", "empty-state");
    return;
  }

  for (const unit of candidates) {
    const label = document.createElement("label");
    label.className = "data-card unit-choice";

    const input = document.createElement("input");
    input.type = "radio";
    input.name = "unit";
    input.value = String(unit.vehicleId);
    input.checked = selected?.vehicleId === unit.vehicleId;
    input.disabled = busy;
    input.addEventListener("change", () => chooseUnit(unit));
    label.appendChild(input);

    const body = document.createElement("div");
    const top = document.createElement("div");
    top.className = "data-card-top";
    addText(top, "h3", `${unit.unitCode} · ${unit.vehicleType}`);
    top.appendChild(unit.example ? makeTag("EJEMPLO") : makeTag("ORACLE", true));
    body.appendChild(top);

    addText(body, "p", unit.stationName ?? "Sin estación");
    addText(body, "p",
      `Llega en ${MINUTES.format(unit.etaMinutes)} min · ${KM.format(unit.distanceKm)} km por las calles`,
      "status-text");

    label.appendChild(body);
    list.appendChild(label);
  }
}

function addButton(parent, text, className, onClick) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = className;
  button.textContent = text;
  button.disabled = busy;
  button.addEventListener("click", onClick);
  parent.appendChild(button);
  return button;
}

/** The close of the emergency asks for a second click, so it is never done by mistake. */
function renderClose(box) {
  if (!canDispatch() || !CLOSABLE.includes(current.status)) {
    return;
  }

  const area = document.createElement("div");
  area.className = "close-area";

  if (!confirmingClose) {
    const text = current.status === "REPORTADA"
      ? "Cerrar como falsa alarma"
      : "Cerrar emergencia";
    addButton(area, text, "secondary-button", () => {
      confirmingClose = true;
      renderPanel();
    });
  } else {
    addText(area, "p",
      "¿Cerrar la emergencia? Se finalizan sus asignaciones, las unidades vuelven a DISPONIBLE "
        + "y sus rutas se cierran, en una sola transacción.",
      "status-text");
    const row = document.createElement("div");
    row.className = "mission-actions";
    addButton(row, "Volver", "secondary-button", () => {
      confirmingClose = false;
      renderPanel();
    });
    addButton(row, "Sí, cerrar", "button dispatch-button", close);
    area.appendChild(row);
  }

  box.appendChild(area);
}

function renderPanel() {
  const box = byId("dispatch-summary");
  box.replaceChildren();
  const button = byId("dispatch-button");
  button.hidden = true;

  if (mission) {
    addText(box, "p", `Misión ${mission.id} · ${unitName(mission.vehicleId)}`);
    const status = addText(box, "p", "", "status-text");
    status.append("Estado de la misión: ");
    status.appendChild(makeTag(mission.status, true));

    const actions = MISSION_ACTIONS[mission.status] ?? [];
    if (canDispatch() && actions.length > 0) {
      const row = document.createElement("div");
      row.className = "mission-actions";
      for (const action of actions) {
        addButton(row, action.text,
          action.status === "CANCELADA" ? "secondary-button" : "primary-button",
          () => advance(action.status));
      }
      box.appendChild(row);
    }

    if (mission.status === "EN_SITIO") {
      addText(box, "p",
        "El traslado al hospital (TRASLADANDO) llega con K7b. Si no hay traslado, cierra la emergencia.",
        "status-text");
    }
  } else if (current.status === "REPORTADA" && canDispatch()) {
    button.hidden = false;
    button.disabled = busy || !selected;
    if (selected) {
      addText(box, "p", `Unidad: ${selected.unitCode} (${selected.vehicleType})`);
      addText(box, "p", `Emergencia: ${current.code} · ${typeLabel(current.type)}`);
      addText(box, "p", `Llega en ${MINUTES.format(selected.etaMinutes)} min por la ruta recomendada.`);
    } else {
      addText(box, "p", "Selecciona una unidad disponible.", "empty-state");
    }
  } else if (!canDispatch()) {
    addText(box, "p", "Tu rol no puede despachar ni cambiar el estado de la misión.", "empty-state");
  } else {
    addText(box, "p", `La emergencia está ${current.status} y no tiene una misión abierta.`, "empty-state");
  }

  const closeBox = byId("close-box");
  closeBox.replaceChildren();
  renderClose(closeBox);
}

/** Shows the 3 routes of the unit without saving anything. quiet keeps the message on screen. */
async function chooseUnit(unit, quiet = false) {
  selected = unit;
  if (!quiet) {
    hideMessage();
  }
  renderPanel();

  if (!canPreview()) {
    clearRoutes("Tu rol no puede calcular rutas. Al despachar, el servidor calcula y guarda las 3 rutas.");
    return;
  }

  const list = byId("route-list");
  list.replaceChildren();
  addText(list, "p", "Calculando rutas…", "empty-state");

  try {
    const routes = await previewRoutes(current.id, unit.vehicleId);
    if (selected !== unit) {
      return;
    }
    drawRoutes(map, routeLayer, routes, list);
    setRouteTag("VISTA PREVIA", false);
    byId("route-note").textContent =
      "Vista previa: todavía no se guarda nada. Al despachar, el servidor vuelve a calcular "
        + "las rutas con el tráfico de ese momento, así que pueden cambiar un poco.";
  } catch (error) {
    clearRoutes(error.message);
  }
}

async function showMissionRoutes() {
  try {
    const routes = await listMissionRoutes(mission.id);
    if (routes.length === 0) {
      clearRoutes(mission.status === "EN_SITIO"
        ? "La unidad llegó al sitio: la ruta de ida quedó FINALIZADA."
        : "Esta misión no tiene rutas en uso.");
      return;
    }
    drawRoutes(map, routeLayer, routes, byId("route-list"));
    setRouteTag("MISIÓN", true);
    byId("route-note").textContent =
      "Rutas guardadas en Oracle al despachar: la ACTIVA es la que sigue la unidad.";
  } catch (error) {
    clearRoutes(error.message);
  }
}

async function dispatch() {
  if (busy || !selected) {
    return;
  }
  busy = true;
  renderCandidates();
  renderPanel();

  try {
    const result = await dispatchUnit(current.id, selected.vehicleId);
    unitCodes.set(selected.vehicleId, selected.unitCode);
    mission = result.assignment;
    candidates = [];
    current = (await findEmergency(current.id)) ?? current;
    drawMarkers();
    drawRoutes(map, routeLayer, result.routes, byId("route-list"));
    setRouteTag("MISIÓN", true);
    byId("route-note").textContent =
      "Rutas guardadas en Oracle al despachar: la ACTIVA es la que sigue la unidad.";
    showMessage(`${selected.unitCode} despachada. La emergencia quedó ${current.status} `
      + "y la unidad OCUPADO.");
  } catch (error) {
    // 409: another coordinator took the unit or the emergency; the list is read again.
    showMessage(error.message, true);
    if (error.status === 409) {
      await loadEmergency(current.id, false);
    }
  } finally {
    busy = false;
    renderEmergency();
    renderCandidates();
    renderPanel();
  }
}

async function advance(status) {
  if (busy) {
    return;
  }
  busy = true;
  renderPanel();

  try {
    mission = await changeMissionStatus(mission.id, status);
    showMessage(`Misión ${mission.id}: ${mission.status}.`);
    await loadEmergency(current.id, false);
  } catch (error) {
    showMessage(error.message, true);
  } finally {
    busy = false;
    renderPanel();
  }
}

async function close() {
  if (busy) {
    return;
  }
  busy = true;
  renderPanel();

  try {
    current = await closeEmergency(current.id);
    mission = null;
    candidates = [];
    selected = null;
    showMessage(`Emergencia ${current.code} cerrada.`);
    renderEmergency();
    drawMarkers();
    renderCandidates();
    clearRoutes("La emergencia está cerrada: sus rutas quedaron FINALIZADA o DESCARTADA.");
  } catch (error) {
    showMessage(error.message, true);
  } finally {
    busy = false;
    confirmingClose = false;
    renderPanel();
  }
}

/** Why there are no routes on screen. */
function routeHint() {
  if (current.status !== "REPORTADA") {
    return "Esta emergencia no tiene rutas en uso.";
  }
  if (!canDispatch()) {
    return "Las rutas las calcula el coordinador al elegir una unidad.";
  }
  if (candidates.length === 0) {
    return "No hay unidades disponibles, así que no hay rutas que mostrar.";
  }
  return "Elige una unidad para ver sus rutas.";
}

/**
 * Reads the emergency again and shows what fits its status: the units that can go if it is
 * REPORTADA, or its open mission. The page does not ask by itself every few seconds: those
 * queries would keep the session alive (DT-08). The live follow-up arrives with /api/tracking.
 */
async function loadEmergency(id, resetMessage = true) {
  selected = null;
  mission = null;
  candidates = [];
  confirmingClose = false;
  if (resetMessage) {
    hideMessage();
  }

  try {
    current = await findEmergency(id);
  } catch (error) {
    showMessage(error.message, true);
    return;
  }
  if (!current) {
    showMessage("Esa emergencia no existe.", true);
    return;
  }

  try {
    if (current.status === "REPORTADA") {
      if (canDispatch()) {
        candidates = await listCandidates(current.id);
        for (const unit of candidates) {
          unitCodes.set(unit.vehicleId, unit.unitCode);
        }
      }
    } else if (current.status !== "CERRADA") {
      mission = await findOpenAssignment(current.id);
      if (mission) {
        await learnUnitCode(mission.vehicleId);
      }
    }
  } catch (error) {
    showMessage(error.message, true);
  }

  renderEmergency();
  drawMarkers();
  renderCandidates();
  renderPanel();

  if (mission) {
    await showMissionRoutes();
  } else if (candidates.length > 0 && canPreview()) {
    await chooseUnit(candidates[0], true);
    renderCandidates();
  } else {
    clearRoutes(routeHint());
  }
}

async function initialize() {
  session = await startPage();
  if (!session) return;

  map = createBaseMap("map");
  if (map) {
    markerLayer = L.layerGroup().addTo(map);
    routeLayer = L.layerGroup().addTo(map);
  }

  byId("dispatch-button").addEventListener("click", dispatch);
  byId("refresh-button").addEventListener("click", () => {
    if (current && !busy) {
      loadEmergency(current.id);
    }
  });

  let emergencies;
  try {
    emergencies = await listEmergencies();
  } catch (error) {
    addText(byId("emergency-detail"), "p", error.message, "empty-state");
    return;
  }

  const select = byId("emergency-select");
  for (const emergency of emergencies) {
    const option = document.createElement("option");
    option.value = String(emergency.id);
    option.textContent = `${emergency.code} · ${typeLabel(emergency.type)} · ${emergency.status}`;
    select.appendChild(option);
  }

  const requested = new URLSearchParams(window.location.search).get("emergencia");
  const first = emergencies.find((emergency) => String(emergency.id) === requested)
    ?? emergencies.find((emergency) => emergency.status === "REPORTADA")
    ?? emergencies[0];

  if (!first) {
    addText(byId("emergency-detail"), "p",
      "No hay emergencias activas. Registra una desde el panel.", "empty-state");
    clearRoutes("Sin emergencia.");
    return;
  }

  select.value = String(first.id);
  select.addEventListener("change", () => loadEmergency(select.value));

  await loadEmergency(first.id);
}

document.addEventListener("DOMContentLoaded", initialize);
