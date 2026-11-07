
// Register emergency page (CU-06).
// The server will repeat all validations when the API is available.

import {
  listEmergencyTypes,
  registerEmergency,
  SEVERITIES,
} from "./api-client.js";

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
      "Emergencia nueva (EJEMPLO)",
    );
  }

  byId("point-text").textContent =
    `Latitud ${NUMBER.format(latitude)}, longitud ${NUMBER.format(longitude)}`;

  byId("form-message").hidden = true;
}

async function fillTypes() {
  const select = byId("type");

  for (const type of await listEmergencyTypes()) {
    const option = document.createElement("option");
    option.value = type.name;
    option.textContent = type.label;
    select.appendChild(option);
  }
}

function fillSeverities() {
  const box = byId("severity-options");

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

  const type = data.get("type");
  const severity = data.get("severity");
  const address = String(data.get("address") ?? "").trim();
  const description = String(data.get("description") ?? "").trim();

  if (!type) {
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
    type,
    severity,
    address,
    description,
    latitude: point[0],
    longitude: point[1],
  };
}

async function initialize() {
  await fillTypes();
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
        `Emergencia ${emergency.code} registrada como REPORTADA (EJEMPLO, no se guardó en Oracle). Abriendo el despacho…`,
        false,
      );

      setTimeout(() => {
        window.location.href =
          `./despacho.html?emergencia=${encodeURIComponent(emergency.code)}`;
      }, 1200);
    } catch (error) {
      console.error("Emergency registration failed:", error);
      showMessage("No se pudo registrar la emergencia. Intenta de nuevo.", true);
      button.disabled = false;
    }
  });
}

document.addEventListener("DOMContentLoaded", initialize);
