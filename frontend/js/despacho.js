
import {
  findEmergency,
  listEmergencies,
  listUnits,
  typeLabel,
  DISPATCH_AVAILABLE,
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

  top.appendChild(makeTag("ORACLE"));
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
        emergency.code,
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
      "El despacho real estará disponible cuando se implemente K7.",
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
      meters: straightMeters(unit.location, current.location),
    }))
    .sort((a, b) => a.meters - b.meters);

  byId("unit-pill").textContent = String(available.length);

  if (available.length === 0) {
    addText(list, "p", "No hay unidades disponibles.", "empty-state");
    return;
  }

  for (const { unit, meters } of available) {
    const label = document.createElement("label");
    label.className = "data-card unit-choice";

    const input = document.createElement("input");
    input.type = "radio";
    input.name = "unit";
    input.value = unit.code;

    input.addEventListener("change", () => {
      selectedUnit = unit;
      renderSummary();
    });

    label.appendChild(input);

    const body = document.createElement("div");

    const top = document.createElement("div");
    top.className = "data-card-top";

    addText(top, "h3", `${unit.code} · ${unit.type}`);
    top.appendChild(makeTag("DISPONIBLE"));

    body.appendChild(top);

    addText(body, "p", unit.place);

    addText(
      body,
      "p",
      `${KM.format(meters / 1000)} km en línea recta`,
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
    tag.textContent = "DEMO";
    tag.className = "data-tag";

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

async function selectEmergency(code, units) {
  current = await findEmergency(code);
  selectedUnit = null;

  byId("dispatch-message").hidden = true;

  if (!current) return;

  renderEmergency(current);
  renderUnits(units);
  renderRoutes(current);
  renderSummary();
}

async function initialize() {
  const session = await startPage();
  if (!session) return;

  try {
    map = createBaseMap("map");

    const units = await listUnits();

    if (map) {
      emergencyLayer = L.layerGroup().addTo(map);

      for (const unit of units) {
        circleMarker(
          map,
          unit.location,
          COLORS.unit,
          `${unit.code} · ${unit.status}`,
        );
      }
    }

    const emergencies = await listEmergencies();
    const select = byId("emergency-select");

    select.replaceChildren();

    for (const emergency of emergencies) {
      const option = document.createElement("option");
      option.value = emergency.code;

      option.textContent =
        `${emergency.code} · ${typeLabel(emergency.type)} · ${emergency.status}`;

      select.appendChild(option);
    }

    const requested =
      new URLSearchParams(window.location.search).get("emergencia");

    const first =
      emergencies.find((emergency) => emergency.code === requested)
      ?? emergencies.find((emergency) => emergency.status === "REPORTADA")
      ?? emergencies[0];

    if (!first) {
      addText(
        byId("emergency-detail"),
        "p",
        "No hay emergencias registradas.",
        "empty-state",
      );

      byId("dispatch-button").disabled = true;
      return;
    }

    select.value = first.code;

    select.addEventListener("change", async () => {
      try {
        await selectEmergency(select.value, units);
      } catch (error) {
        showMessage(error.message, true);
      }
    });

    await selectEmergency(first.code, units);

    const button = byId("dispatch-button");
    button.disabled = true;

    button.addEventListener("click", () => {
      showMessage(
        "El despacho real estará disponible cuando se implemente K7.",
        true,
      );
    });

  } catch (error) {
    console.error("Dispatch page initialization failed:", error);
    showMessage(error.message, true);
  }
}

document.addEventListener("DOMContentLoaded", initialize);
