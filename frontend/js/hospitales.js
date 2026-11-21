// Hospitales page: the hospitals of Valledupar that receive the transfers, on a list and on
// the map (docs/api.md 7.4). It only reads: hospitals are created and edited in Administración.
import { hasPermission, listHospitals } from "./api-client.js";
import { paintLegend, placeIcon } from "./map-icons.js";
import { startPage } from "./page-session.js";
import {
  addText,
  byId,
  createResourceMap,
  focusOn,
  listMessage,
  placeMarker,
  plain,
  resourceCard,
} from "./resource-page.js";
import { HOSPITALS as D2_HOSPITALS, SOURCES_CHECKED_ON } from "./valledupar-data.js";

let hospitals = [];
const markers = new Map();

function capacityText(hospital) {
  return hospital.emergencyCapacity == null
    ? "Capacidad de urgencias: sin dato verificado"
    : `Capacidad de urgencias: ${hospital.emergencyCapacity}`;
}

function phoneLine(hospital) {
  if (!hospital.phone) {
    return "Teléfono: sin dato";
  }
  const line = document.createElement("p");
  line.append("Teléfono: ");
  const link = document.createElement("a");
  link.href = `tel:${hospital.phone.replace(/[^\d+]/g, "")}`;
  link.textContent = hospital.phone;
  // The card is a button: calling must not also move the map.
  link.addEventListener("click", (event) => event.stopPropagation());
  line.appendChild(link);
  return line;
}

function sourceLine(hospital) {
  const source = D2_HOSPITALS.find((item) => item.name === hospital.name)?.source;
  if (!source) {
    return null;
  }
  const link = document.createElement("a");
  link.href = source;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = "Fuente del dato";
  link.className = "resource-source";
  link.addEventListener("click", (event) => event.stopPropagation());
  return link;
}

function visible() {
  const text = plain(byId("hospital-search").value.trim());
  const level = byId("hospital-level").value;
  return hospitals.filter((hospital) =>
    (!text || plain(`${hospital.name} ${hospital.address}`).includes(text))
    && (!level || hospital.level === level));
}

function render(map) {
  const list = byId("hospital-list");
  const shown = visible();
  byId("hospital-pill").textContent = String(shown.length);
  if (shown.length === 0) {
    listMessage(list, hospitals.length === 0
      ? "No hay hospitales activos."
      : "Ningún hospital coincide con la búsqueda.");
    return;
  }
  list.replaceChildren(...shown.map((hospital) => {
    const marker = markers.get(hospital.id);
    return resourceCard({
      title: hospital.name,
      tag: "ORACLE · HOSPITAL",
      lines: [
        `Nivel ${hospital.level ?? "sin dato"} · ${capacityText(hospital)}`,
        hospital.address || "Dirección no disponible",
        phoneLine(hospital),
        marker ? null : "Sin posición conocida en el mapa",
        sourceLine(hospital),
      ],
      onSelect: marker ? () => focusOn(map, marker) : null,
    });
  }));
}

async function initialize() {
  paintLegend();
  const session = await startPage();
  if (!session) {
    return;
  }
  const list = byId("hospital-list");
  if (!hasPermission(session, "HOSPITALES:CONSULTAR")) {
    listMessage(list, "Tu rol no puede consultar los hospitales.", true);
    return;
  }
  byId("source-date").textContent = `Fecha de revisión de fuentes: ${SOURCES_CHECKED_ON}`;

  const map = createResourceMap("map");
  try {
    hospitals = await listHospitals();
  } catch (error) {
    listMessage(list, `No fue posible cargar los hospitales: ${error.message}`, true);
    return;
  }
  hospitals.sort((a, b) => a.name.localeCompare(b.name, "es"));
  for (const hospital of hospitals) {
    const marker = placeMarker(map, hospital.location, placeIcon("hospital"), hospital.name, [
      `Nivel ${hospital.level ?? "sin dato"}`,
      hospital.address,
      hospital.phone ? `Teléfono: ${hospital.phone}` : null,
    ]);
    if (marker) {
      markers.set(hospital.id, marker);
    }
  }

  byId("hospital-search").addEventListener("input", () => render(map));
  byId("hospital-level").addEventListener("change", () => render(map));
  render(map);
  addText(byId("hospital-total"), "span", `${hospitals.length} hospitales activos en Oracle`);
  if (map) {
    requestAnimationFrame(() => map.invalidateSize());
  }
}

initialize();
