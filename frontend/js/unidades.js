// Unidades page: ambulances, fire trucks and police patrols with their status, and the bases
// they belong to (docs/api.md 7.2 and 7.3). The map is the live one (D7): positions and status
// come from /api/tracking every 5 s and are SIMULACION. It only reads: units are registered and
// changed in Administración.
import { hasPermission, listStations, listUnits } from "./api-client.js";
import { startLiveMap } from "./live-map.js";
import { paintLegend, placeIcon, UNIT_Z, unitIcon, vehicleLabel } from "./map-icons.js";
import { startPage } from "./page-session.js";
import {
  byId,
  createResourceMap,
  focusOn,
  listMessage,
  placeMarker,
  plain,
  resourceCard,
} from "./resource-page.js";

const STATUS = {
  DISPONIBLE: { text: "Disponible", className: "is-available" },
  OCUPADO: { text: "En misión", className: "is-busy" },
  MANTENIMIENTO: { text: "Mantenimiento", className: "is-off" },
  FUERA_SERVICIO: { text: "Fuera de servicio", className: "is-off" },
};

let units = [];
let stations = [];
let lastDrawn = "";
const staticMarkers = new Map();
const stationMarkers = new Map();

function statusOf(code) {
  return STATUS[code] ?? { text: code ?? "Sin estado", className: "is-off" };
}

function visibleUnits() {
  const text = plain(byId("unit-search").value.trim());
  const type = byId("unit-type").value;
  const status = byId("unit-status").value;
  return units.filter((unit) =>
    (!text || plain(`${unit.code} ${unit.place}`).includes(text))
    && (!type || unit.type === type)
    && (!status || unit.status === status));
}

function renderSummary() {
  const count = (code) => units.filter((unit) => unit.status === code).length;
  const off = count("MANTENIMIENTO") + count("FUERA_SERVICIO");
  byId("count-available").textContent = String(count("DISPONIBLE"));
  byId("count-busy").textContent = String(count("OCUPADO"));
  byId("count-off").textContent = String(off);
}

function renderUnits(map, live) {
  renderSummary();
  const list = byId("unit-list");
  const shown = visibleUnits();
  byId("unit-pill").textContent = String(shown.length);
  if (shown.length === 0) {
    listMessage(list, units.length === 0 ? "No hay unidades registradas." : "Ninguna unidad coincide con el filtro.");
    return;
  }
  list.replaceChildren(...shown.map((unit) => {
    const focus = live
      ? () => {
        if (live.focus(unit.id) && window.matchMedia("(max-width: 899.98px)").matches) {
          map.getContainer().scrollIntoView({ behavior: "smooth", block: "center" });
        }
      }
      : staticMarkers.has(unit.id) ? () => focusOn(map, staticMarkers.get(unit.id)) : null;
    return resourceCard({
      title: unit.code,
      tag: unit.example ? "EJEMPLO" : "ORACLE",
      tagIsReal: !unit.example,
      status: statusOf(unit.status),
      lines: [
        vehicleLabel(unit.type),
        `Base: ${unit.place}`,
        unit.location ? null : "Sin posición conocida",
      ],
      onSelect: unit.location ? focus : null,
      hint: "Clic para verla en el mapa",
    });
  }));
}

function renderStations(map) {
  const list = byId("station-list");
  byId("station-pill").textContent = String(stations.length);
  if (stations.length === 0) {
    listMessage(list, "No hay estaciones activas.");
    return;
  }
  list.replaceChildren(...stations.map((station) => {
    const assigned = units.filter((unit) => unit.place === station.name).length;
    const marker = stationMarkers.get(station.id);
    return resourceCard({
      title: station.name,
      tag: station.name.startsWith("EJEMPLO") ? "EJEMPLO" : "ORACLE · ESTACIÓN",
      tagIsReal: !station.name.startsWith("EJEMPLO"),
      lines: [
        station.address || "Dirección no disponible",
        `${assigned} ${assigned === 1 ? "unidad registrada" : "unidades registradas"}`,
      ],
      onSelect: marker ? () => focusOn(map, marker) : null,
      hint: "Clic para verla en el mapa",
    });
  }));
}

/**
 * The live snapshot brings the current status and position of every unit: the list is drawn
 * again only when one of them changed status, so a click or the keyboard focus is not lost
 * every 5 seconds.
 */
function applySnapshot(snapshot, map, live) {
  const now = new Map((snapshot.units ?? []).map((unit) => [unit.vehicleId, unit]));
  for (const unit of units) {
    const current = now.get(unit.id);
    if (current) {
      unit.status = current.operationalStatus;
      unit.location = current.location ?? unit.location;
    }
  }
  const drawn = units.map((unit) => `${unit.id}:${unit.status}:${Boolean(unit.location)}`).join("|");
  if (drawn !== lastDrawn) {
    lastDrawn = drawn;
    renderUnits(map, live);
  }
}

async function initialize() {
  paintLegend();
  const session = await startPage();
  if (!session) {
    return;
  }
  if (!hasPermission(session, "UNIDADES:CONSULTAR")) {
    listMessage(byId("unit-list"), "Tu rol no puede consultar las unidades.", true);
    return;
  }

  const map = createResourceMap("map");
  try {
    [units, stations] = await Promise.all([listUnits(), listStations()]);
  } catch (error) {
    listMessage(byId("unit-list"), `No fue posible cargar las unidades: ${error.message}`, true);
    return;
  }
  units.sort((a, b) => a.code.localeCompare(b.code, "es"));
  stations.sort((a, b) => a.name.localeCompare(b.name, "es"));

  for (const station of stations) {
    const marker = placeMarker(map, station.location, placeIcon("station", station), station.name, [station.address]);
    if (marker) {
      stationMarkers.set(station.id, marker);
    }
  }

  // Live positions when the role can follow the operation; otherwise the last known ones.
  const live = startLiveMap(map, session, {
    onSnapshot: (snapshot) => applySnapshot(snapshot, map, live),
    onError: () => {
      byId("unit-live").textContent = "Seguimiento no disponible: posiciones de la última consulta";
    },
  });
  if (!live) {
    byId("unit-live").textContent = "Posiciones de la última consulta (sin seguimiento en vivo)";
    for (const unit of units) {
      const marker = placeMarker(map, unit.location, unitIcon(unit.type, unit.status), unit.code,
        [`${vehicleLabel(unit.type)} · ${statusOf(unit.status).text}`, `Base: ${unit.place}`]);
      if (marker) {
        marker.setZIndexOffset(UNIT_Z);
        staticMarkers.set(unit.id, marker);
      }
    }
  }

  for (const id of ["unit-search", "unit-type", "unit-status"]) {
    byId(id).addEventListener(id === "unit-search" ? "input" : "change", () => renderUnits(map, live));
  }
  renderUnits(map, live);
  renderStations(map);
  if (map) {
    requestAnimationFrame(() => map.invalidateSize());
  }
}

initialize();
