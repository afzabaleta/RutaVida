
import {
  listEmergencyTypes,
  registerEmergency,
  SEVERITIES,
} from "./api-client.js";

import { startPage } from "./page-session.js";

import {
  byId,
  addText,
  circleMarker,
  createBaseMap,
  insideZone,
  COLORS,
  DEMO_POINT,
} from "./map-base.js";

const NUMBER = new Intl.NumberFormat("es-CO", {
  maximumFractionDigits: 6,
  minimumFractionDigits: 6,
});

let point = null;
let pointMarker = null;

function showMessage(text, isError) {
  const message = byId("form-message");
  message.textContent = text;
  message.className = isError ? "form-message error" : "form-message ok";
  message.hidden = false;
}

function setPoint(map, latitude, longitude) {
  if (!insideZone(latitude, longitude)) {
    showMessage(
      "Ese punto está fuera de la zona de la red vial cargada. Marca un lugar dentro del recuadro.",
      true,
    );
    return;
  }

  point = [latitude, longitude];

  if (map && pointMarker) {
    pointMarker.setLatLng(point);
  } else if (map) {
    pointMarker = circleMarker(
      map,
      point,
      COLORS.emergency,
      "Ubicación de la emergencia",
    );
  }

  byId("point-text").textContent =
    `Latitud ${NUMBER.format(latitude)}, longitud ${NUMBER.format(longitude)}`;

  byId("form-message").hidden = true;
}

async function fillTypes() {
  const select = byId("type");
  select.replaceChildren();

  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = "Selecciona el tipo de emergencia";
  select.appendChild(placeholder);

  for (const type of await listEmergencyTypes()) {
    const option = document.createElement("option");
    option.value = type.id;
    option.textContent = type.label;
    select.appendChild(option);
  }
}

function fillSeverities() {
  const box = byId("severity-options");
  box.replaceChildren();

  for (const severity of SEVERITIES) {
    const label = document.createElement("label");
    label.className = `severity-chip severity-${severity.code.toLowerCase()}`;

    const input = document.createElement("input");
    input.type = "radio";
    input.name = "severity";
    input.value = severity.code;

    label.appendChild(input);
    addText(label, "span", severity.label);
    box.appendChild(label);
  }
}

function readForm(form) {
  const data = new FormData(form);
  const typeId = data.get("type");
  const severity = data.get("severity");
  const address = String(data.get("address") ?? "").trim();
  const description = String(data.get("description") ?? "").trim();

  if (!typeId) {
    showMessage("Selecciona el tipo de emergencia.", true);
    return null;
  }

  if (!severity) {
    showMessage("Selecciona la severidad.", true);
    return null;
  }

  if (address.length < 5) {
    showMessage(
      "Escribe una dirección o referencia de al menos 5 caracteres.",
      true,
    );
    return null;
  }

  if (!point) {
    showMessage("Marca en el mapa el lugar de la emergencia.", true);
    return null;
  }

  return {
    typeId,
    severity,
    address,
    description,
    latitude: point[0],
    longitude: point[1],
  };
}

async function initialize() {
  const session = await startPage();
  if (!session) return;

  try {
    await fillTypes();
  } catch (error) {
    showMessage(error.message, true);
    return;
  }

  fillSeverities();

  const map = createBaseMap("map");

  if (map) {
    map.on("click", (event) => {
      setPoint(map, event.latlng.lat, event.latlng.lng);
    });
  }

  byId("demo-point").addEventListener("click", () => {
    setPoint(map, DEMO_POINT[0], DEMO_POINT[1]);

    if (map) {
      map.setView(DEMO_POINT, 16);
    }
  });

  const form = byId("emergency-form");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const request = readForm(form);
    if (!request) return;

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;

    try {
      const emergency = await registerEmergency(request);

      showMessage(
        `Emergencia ${emergency.code} registrada correctamente en Oracle.`,
        false,
      );
    } catch (error) {
      console.error("Emergency registration failed:", error);
      showMessage(error.message, true);
      button.disabled = false;
    }
  });
}

document.addEventListener("DOMContentLoaded", initialize);
