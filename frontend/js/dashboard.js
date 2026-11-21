
import {
  getDashboardSummary,
  hasPermission,
  homeOf,
  listEmergencies,
  listHospitals,
  listStations,
  listUnits,
  typeLabel,
} from "./api-client.js";

import { startIncidents } from "./incident-panel.js";
import { startIncidentForm } from "./incident-form.js";
import { startPanicLayer } from "./panic-layer.js";
import { paintLegend, placeIcon, UNIT_Z, unitIcon, unitSlots } from "./map-icons.js";

import {
  STATIONS as D2_STATIONS,
  HOSPITALS as D2_HOSPITALS,
  ROAD_NETWORK_ZONE,
} from "./valledupar-data.js";

import { startLiveMap } from "./live-map.js";
import { startSimulationPanel } from "./simulation-panel.js";
import { startAlertBell } from "./alert-bell.js";
import { startPage } from "./page-session.js";

const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

const byId = (id) => document.getElementById(id);

function addText(parent, tag, text, className = "") {
  const element = document.createElement(tag);
  element.textContent = String(text ?? "");

  if (className) {
    element.className = className;
  }

  parent.appendChild(element);
  return element;
}

function createMap() {
  if (typeof L === "undefined") {
    const container = byId("map");

    if (container) {
      container.textContent =
        "No se pudo cargar Leaflet. Comprueba la conexión a internet.";
    }

    return null;
  }

  const map = L.map("map").setView([10.4631, -73.2532], 13);

  L.tileLayer(TILE_URL, {
    attribution: "&copy; OpenStreetMap contributors",
    maxZoom: 19,
  }).addTo(map);

  if (ROAD_NETWORK_ZONE) {
    const { south, west, north, east } = ROAD_NETWORK_ZONE;

    const bounds = [
      [south, west],
      [north, east],
    ];

    L.rectangle(bounds, {
      color: "#79adff",
      weight: 1,
      opacity: 0.6,
      fillOpacity: 0.03,
      interactive: false,
    }).addTo(map);
  }

  return map;
}

function makePopup(title, description, label, source = null) {
  const wrapper = document.createElement("div");

  const heading = addText(wrapper, "strong", title);
  heading.style.display = "block";

  addText(wrapper, "p", description || "Sin información");
  addText(wrapper, "small", label);

  if (source) {
    const link = document.createElement("a");
    link.href = source;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = "Consultar fuente";
    link.style.display = "block";
    wrapper.appendChild(link);
  }

  return wrapper;
}

function addMarker(map, item, icon, popup, zIndexOffset = 0) {
  if (!map || !Array.isArray(item.location)) {
    return null;
  }

  const [lat, lng] = item.location;

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }

  const marker = L.marker([lat, lng], { icon, keyboard: false, zIndexOffset }).addTo(map);

  marker.bindPopup(popup);

  return marker;
}

function focusMarker(map, marker) {
  if (!map || !marker) {
    return;
  }

  map.setView(
    marker.getLatLng(),
    Math.max(map.getZoom(), 15),
  );

  marker.openPopup();
}

function makeDataCard({
  title,
  label,
  description,
  status,
  action,
}) {
  const card = document.createElement("article");
  card.className = "data-card";

  const heading = document.createElement("div");
  heading.className = "data-card-top";

  addText(heading, "strong", title);
  addText(
    heading,
    "span",
    label,
    label.startsWith("EJEMPLO") ? "data-tag" : "data-tag real-tag",
  );

  card.appendChild(heading);

  if (description) {
    addText(card, "p", description);
  }

  if (status) {
    addText(card, "small", status, "muted");
  }

  if (typeof action === "function") {
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.setAttribute(
      "aria-label",
      `Ver ${title} en el mapa`,
    );

    card.addEventListener("click", action);

    card.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        action();
      }
    });
  }

  return card;
}

function showFailure(list, error) {
  if (!list) {
    return;
  }

  list.replaceChildren();

  const message =
    error instanceof Error
      ? error.message
      : String(
          error ??
          "No fue posible cargar los datos desde la API.",
        );

  addText(
    list,
    "p",
    `No fue posible cargar los datos: ${message}`,
    "muted",
  );
}

function showEmpty(list, message) {
  if (list && !list.hasChildNodes()) {
    addText(list, "p", message, "muted");
  }
}

function findReference(collection, name) {
  return collection.find((item) => item.name === name);
}

/**
 * Stations and hospitals on the map. Their lists are on their own pages now
 * (hospitales.html and unidades.html), so the panel only draws the markers.
 */
function renderPlaces(map, stations, hospitals) {
  for (const station of stations) {
    const reference = findReference(D2_STATIONS, station.name);
    const item = { ...station, location: station.location || reference?.location || null };

    addMarker(
      map,
      item,
      placeIcon("station", item),
      makePopup(
        station.name,
        station.address || "Dirección no disponible",
        "ORACLE · Estación activa",
        reference?.source,
      ),
    );
  }

  for (const hospital of hospitals) {
    const reference = findReference(D2_HOSPITALS, hospital.name);
    const item = { ...hospital, location: hospital.location || reference?.location || null };

    addMarker(
      map,
      item,
      placeIcon("hospital"),
      makePopup(
        hospital.name,
        `${hospital.address || "Dirección no disponible"} · Nivel ${hospital.level || "Sin datos"}`,
        "ORACLE · Hospital",
        reference?.source,
      ),
    );
  }
}

function renderEmergencies(map, emergencies) {
  const list = byId("emergency-list");
  list.replaceChildren();


 byId("emergency-pill").textContent = String(emergencies.length);
if (!usesSummary) {
  byId("emergency-count").textContent = String(emergencies.length);
}
  for (const emergency of emergencies) {
    const title =
      emergency.code ||
      `Emergencia ${emergency.id ?? ""}`;

    const description =
      `${typeLabel(emergency.type)} · ` +
      `${emergency.severity || "Sin severidad"} · ` +
      `${emergency.status || "Sin estado"}`;

    const marker = addMarker(
      map,
      emergency,
      placeIcon("emergency"),
      makePopup(
        title,
        description,
        "ORACLE · Emergencia",
      ),
    );

    list.appendChild(
      makeDataCard({
        title,
        label: "ORACLE",
        description,
        status:
          emergency.place ||
          (marker ? "" : "Sin posición conocida"),
        action: marker
          ? () => focusMarker(map, marker)
          : null,
      }),
    );
  }

  showEmpty(
    list,
    "No hay emergencias registradas.",
  );
}

/** Available units for the indicator when the role cannot read the summary (REPORTES). */
function countUnits(units) {
  if (!usesSummary) {
    byId("unit-count").textContent = String(units.filter((unit) => unit.status === "DISPONIBLE").length);
  }
}

let usesSummary = false;

async function refreshDashboardSummary() {
  try {
    const summary = await getDashboardSummary();

    byId("emergency-count").textContent =
      String(summary.activeEmergencies ?? "—");

    byId("unit-count").textContent =
      String(summary.availableUnits ?? "—");

    byId("hospital-count").textContent =
      String(summary.operationalHospitals ?? "—");

  byId("response-time").textContent =
    summary.averageResponseMinutes == null
      ? "Sin datos"
      : `${summary.averageResponseMinutes.toLocaleString("es-CO")} min`;
  } catch (error) {
    console.error("No fue posible actualizar los indicadores:", error);
  }
}


async function initializeDashboard() {
  paintLegend();
  const session = await startPage();

  if (!session) {
    return;
  }


  // Police officers and citizens (PANICO:REPORTAR without dispatch) work from the panic
  // button page (D9, DT-13).
  if (homeOf(session) !== "./dashboard.html") {
    window.location.replace(homeOf(session));
    return;
  }

  usesSummary = hasPermission(session, "REPORTES:CONSULTAR");

  if (usesSummary) {
    refreshDashboardSummary();
    setInterval(refreshDashboardSummary, 30000);
  }


  const map = createMap();

  const bell = startAlertBell({
    button: byId("alert-bell"),
    count: byId("alert-count"),
    panel: byId("alert-panel"),
    list: byId("alert-list"),
  });

  // D12: simulation control and traffic layer.
  const simulation = startSimulationPanel(map, session, {
    panel: byId("sim-panel"),
    stateText: byId("sim-state"),
    startButton: byId("sim-start"),
    stopButton: byId("sim-stop"),
    congestButton: byId("sim-congest"),
    releaseButton: byId("sim-release"),
    message: byId("sim-message"),
    liveStatus: byId("live-status"),
    toast: byId("route-toast"),
  });

  let unitCodes = new Map();

  const live = startLiveMap(map, session, {
    onRouteChanged(mission, route) {
      const unit = unitCodes.get(mission.vehicleId)
        ?? `la unidad ${mission.vehicleId}`;

      simulation?.routeChanged(
        `Ruta recalculada: ${unit} toma un camino nuevo (${route.distanceKm} km, `
        + `${route.estimatedMinutes} min). Tráfico SIMULADO.`
      );
    },

    onSnapshot(snapshot) {
      unitCodes = new Map(
        (snapshot.units ?? []).map((unit) => [
          unit.vehicleId,
          unit.unitCode,
        ])
      );
      const status = byId("live-status");
      if (status && !simulation) {
        status.textContent = "Seguimiento activo · datos SIMULACIÓN";
      }

      bell.setCount(snapshot.unreadAlerts ?? 0);

      const missionList = byId("mission-list");
      if (missionList) {
        missionList.replaceChildren();

        // The mission only brings the vehicle id; its code comes in the units of the same snapshot.
        const unitsById = new Map(
          (snapshot.units ?? []).map((unit) => [unit.vehicleId, unit])
        );

        for (const mission of snapshot.missions ?? []) {
          const unit = unitsById.get(mission.vehicleId);
          const card = makeDataCard({
            title: `Misión ${mission.assignmentId} · Emergencia #${mission.emergencyId}`,
            label: unit?.example ? "EJEMPLO" : "ORACLE",
            description: `Unidad ${unit?.unitCode ?? mission.vehicleId ?? "Sin datos"}`,
            status: mission.status ?? "En seguimiento",
            action: mission.activeRouteId != null
              ? () => live.showRoute(mission.assignmentId)
              : null,
          });

          missionList.appendChild(card);
        }

        showEmpty(missionList, "No hay misiones activas.");
      }
    },

    onError(error) {
      console.error("Error de seguimiento D7:", error);
      const status = byId("live-status");
      if (status) {
        status.textContent = "Seguimiento no disponible";
      }
    },
  });

  if (!live) {
    const status = byId("live-status");
    if (status) {
      status.textContent = "Seguimiento sin permiso o mapa no disponible";
    }
  }

  startPanicLayer(map, session, {
    list: byId("panic-list"),
    pill: byId("panic-pill"),
    message: byId("panic-message"),
  });
const incidents = startIncidents(
  map,
  session,
  {
    list: byId("incident-list"),
    pill: byId("incident-pill"),
    message: byId("incident-message"),
  },
   () => {
     if (usesSummary) {
       refreshDashboardSummary();
     }
   }
);

// Report an incident with a click on the street (CU-13); the list shows it at once.
startIncidentForm(map, session, {
    openButton: byId("incident-report-button"),
    form: byId("incident-form"),
    street: byId("incident-street"),
    bothWays: byId("incident-both"),
    bothWaysField: byId("incident-both-field"),
    message: byId("incident-message"),
}, () => incidents.refresh());



  byId("response-time").textContent = "Sin datos";
  byId("hospital-count").textContent = "—";
  byId("emergency-list").replaceChildren();

  const results = await Promise.allSettled([
    listEmergencies(),
    listUnits(),
    listStations(),
    listHospitals(),
  ]);

  const [
    emergencies,
    units,
    stations,
    hospitals,
  ] = results;

  if (emergencies.status === "fulfilled") {
    renderEmergencies(
      map,
      emergencies.value,
    );
  } else {
    showFailure(
      byId("emergency-list"),
      emergencies.reason,
    );
  }

  if (units.status === "fulfilled") {
    countUnits(units.value);
  } else {
    console.error("No fue posible contar las unidades:", units.reason);
  }

  if (
    stations.status === "fulfilled" &&
    hospitals.status === "fulfilled"
  ) {
    renderPlaces(
      map,
      stations.value,
      hospitals.value,
    );
    if (!usesSummary) {
      byId("hospital-count").textContent = String(hospitals.value.length);
    }
  } else {
    console.error(
      "No fue posible dibujar estaciones y hospitales:",
      stations.status === "rejected" ? stations.reason : hospitals.reason,
    );
  }

  // D7: el mapa en vivo ya se inicializo.
  if (map) {
    requestAnimationFrame(() => map.invalidateSize());
  }
}

if (document.readyState === "loading") {
  document.addEventListener(
    "DOMContentLoaded",
    initializeDashboard,
    { once: true },
  );
} else {
  initializeDashboard();
}
