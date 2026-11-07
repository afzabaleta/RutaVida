
// Dispatch page (CU-08 and CU-09). Emergencies and units come from the API.
// Units are ordered by straight-line distance only as a reference.
// Saving the dispatch needs the dispatch routes of the API (K7).

import {
  DISPATCH_AVAILABLE,
  findEmergency,
  listEmergencies,
  listUnits,
  typeLabel,
} from "./api-client.js";

import { startPage } from "./page-session.js";

import {
  byId,
  addText,
  makeTag,
  circleMarker,
  createBaseMap,
  straightMeters,
  COLORS,
  DEMO_POINT,
} from "./map-base.js";

import { showRoutes } from "./route-layer.js";

const KM = new Intl.NumberFormat("es-CO", {
  maximumFractionDigits: 2,
});

const DEMO_RADIUS_METERS = 50;

let map = null;
let emergencyLayer = null;
let selectedUnit = null;
let current = null;

function showMessage(text, isError) {
  const message = byId("dispatch-message");
  message.textContent = text;
  message.className = isError
    ? "form-message error"
    : "form-message ok";
  message.hidden = false;
}

function renderEmergency(emergency) {
  const box = byId("emergency-detail");
  box.replaceChildren();

  const card = document.createElement("article");
  card.className = "data-card";

  const top = document.createElement("div");
  top.className = "data-card-top";

  addText(
    top,
    "h3",
    `${emergency.code} · ${typeLabel(emergency.type)}`,
  );

  top.appendChild(makeTag("ORACLE", true));
  card.appendChild(top);

  addText(card, "p", emergency.place);

  addText(
    card,
    "p",
    `Severidad ${emergency.severity} · Estado ${emergency.status}`,
    "status-text",
  );

  box.appendChild(card);

  if (map) {
    emergencyLayer.clearLayers();

    emergencyLayer.addLayer(
      circleMarker(
        map,
        emergency.location,
        COLORS.emergency,
        `Emergencia ${emergency.code}`,
      ),
    );

    map.setView(emergency.location, 15);
  }
}

function renderSummary() {
  const box = byId("dispatch-summary");
  box.replaceChildren();

  const button = byId("dispatch-button");

  if (!selectedUnit || !current) {
    addText(
      box,
      "p",
      "Selecciona una unidad disponible.",
      "empty-state",
    );

    button.disabled = true;
    return;
  }

  addText(
    box,
    "p",
    `Unidad: ${selectedUnit.code} (${selectedUnit.type})`,
  );

  addText(
    box,
    "p",
    `Emergencia: ${current.code} · ${typeLabel(current.type)}`,
  );

  addText(
    box,
    "p",
    "Ruta: la recomendada por el motor (ruta 1).",
  );

  button.disabled =
    !DISPATCH_AVAILABLE || current.status !== "REPORTADA";

  if (!DISPATCH_AVAILABLE) {
    addText(
      box,
      "p",
      "El despacho se guardará cuando esté la API de despacho (K7). Por ahora puedes revisar la unidad y las rutas.",
      "status-text",
    );
  } else if (current.status !== "REPORTADA") {
    addText(
      box,
      "p",
      `La emergencia está ${current.status}: solo se despacha una emergencia REPORTADA.`,
      "status-text",
    );
  }
}

function renderUnits(units) {
  const list = byId("unit-list");
  list.replaceChildren();

  const available = units
    .filter((unit) => unit.status === "DISPONIBLE")
    .map((unit) => ({
      unit,
      meters: unit.location
        ? straightMeters(unit.location, current.location)
        : Number.POSITIVE_INFINITY,
    }))
    .sort((a, b) => a.meters - b.meters);

  byId("unit-pill").textContent = String(available.length);

  if (available.length === 0) {
    addText(
      list,
      "p",
      "No hay unidades disponibles.",
      "empty-state",
    );
    return;
  }

  for (const { unit, meters } of available) {
    const label = document.createElement("label");
    label.className = "data-card unit-choice";

    const input = document.createElement("input");
    input.type = "radio";
    input.name = "unit";
    input.value = String(unit.id);

    input.addEventListener("change", () => {
      selectedUnit = unit;
      renderSummary();
    });

    label.appendChild(input);

    const body = document.createElement("div");

    const top = document.createElement("div");
    top.className = "data-card-top";

    addText(top, "h3", `${unit.code} · ${unit.type}`);

    top.appendChild(
      unit.example
        ? makeTag("EJEMPLO")
        : makeTag("ORACLE", true),
    );

    body.appendChild(top);
    addText(body, "p", unit.place);

    addText(
      body,
      "p",
      Number.isFinite(meters)
        ? `${KM.format(meters / 1000)} km en línea recta`
        : "Sin posición conocida",
      "status-text",
    );

    label.appendChild(body);
    list.appendChild(label);
  }
}

function renderRoutes(emergency) {
  const elements = {
    tabs: byId("route-tabs"),
    list: byId("route-list"),
    note: byId("route-note"),
  };

  const tag = byId("route-tag");

  const isDemoPlace =
    straightMeters(emergency.location, DEMO_POINT)
    <= DEMO_RADIUS_METERS;

  if (map && isDemoPlace && window.RUTAVIDA_RUTAS_DEMO) {
    tag.textContent = "CALCULADAS";
    tag.className = "data-tag real-tag";

    showRoutes(map, window.RUTAVIDA_RUTAS_DEMO, elements);
    return;
  }

  tag.textContent = "SIN CALCULAR";
  tag.className = "data-tag";

  elements.tabs.replaceChildren();
  elements.list.replaceChildren();

  addText(
    elements.list,
    "p",
    "Las rutas de este punto las calculará RouteService cuando exista la API. Por ahora solo hay rutas calculadas para el Parque La Popa (demo).",
    "empty-state",
  );

  elements.note.textContent = "";
}

async function selectEmergency(id, units) {
  selectedUnit = null;
  byId("dispatch-message").hidden = true;

  try {
    current = await findEmergency(id);
  } catch (error) {
    console.error("Emergency could not be loaded:", error);
    current = null;
    showMessage(error.message, true);
    return;
  }

  if (!current) {
    showMessage("Esa emergencia no existe.", true);
    return;
  }

  renderEmergency(current);
  renderUnits(units);
  renderRoutes(current);
  renderSummary();
}

async function initialize() {
  const session = await startPage();
  if (!session) return;

  map = createBaseMap("map");

  let units;
  let emergencies;

  try {
    [units, emergencies] = await Promise.all([
      listUnits(),
      listEmergencies(),
    ]);
  } catch (error) {
    // For example 403: the role cannot see the units, or 503: Oracle is down.
    console.error("Dispatch data could not be loaded:", error);

    addText(
      byId("emergency-detail"),
      "p",
      error.message,
      "empty-state",
    );

    return;
  }

  if (map) {
    emergencyLayer = L.layerGroup().addTo(map);

    for (const unit of units) {
      if (!unit.location) continue;

      circleMarker(
        map,
        unit.location,
        COLORS.unit,
        `${unit.code} · ${unit.status}${unit.example ? " (EJEMPLO)" : ""}`,
      );
    }
  }

  const select = byId("emergency-select");

  for (const emergency of emergencies) {
    const option = document.createElement("option");
    option.value = String(emergency.id);

    option.textContent =
      `${emergency.code} · ${typeLabel(emergency.type)} · ${emergency.status}`;

    select.appendChild(option);
  }

  const requested =
    new URLSearchParams(window.location.search).get("emergencia");

  const first =
    emergencies.find(
      (emergency) => String(emergency.id) === requested,
    )
    ?? emergencies.find(
      (emergency) => emergency.status === "REPORTADA",
    )
    ?? emergencies[0];

  if (!first) {
    addText(
      byId("emergency-detail"),
      "p",
      "No hay emergencias activas. Registra una desde el panel.",
      "empty-state",
    );
    return;
  }

  select.value = String(first.id);

  select.addEventListener("change", () => {
    selectEmergency(select.value, units);
  });

  await selectEmergency(first.id, units);
}

document.addEventListener("DOMContentLoaded", initialize);
