
import {
  getDashboardSummary,
  hasPermission,
  listEmergencies,
  listHospitals,
  listStations,
  listUnits,
  typeLabel,
} from "./api-client.js";

import { startIncidents } from "./incident-panel.js";

import {
  STATIONS as D2_STATIONS,
  HOSPITALS as D2_HOSPITALS,
  ROAD_NETWORK_ZONE,
  SOURCES_CHECKED_ON,
} from "./valledupar-data.js";

import { showRoutes } from "./route-layer.js";
import { startPage } from "./page-session.js";

const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

const COLORS = {
  station: "#4bd6a0",
  hospital: "#79adff",
  emergency: "#ff6674",
  unit: "#f6b94b",
};

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

function addMarker(map, item, color, popup) {
  if (!map || !Array.isArray(item.location)) {
    return null;
  }

  const [lat, lng] = item.location;

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }

  const marker = L.circleMarker([lat, lng], {
    radius: 8,
    color,
    fillColor: color,
    fillOpacity: 0.85,
    weight: 2,
  }).addTo(map);

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

function renderPlaces(map, stations, hospitals) {
  const list = byId("hospital-list");
  list.replaceChildren();

  for (const station of stations) {
    const reference = findReference(
      D2_STATIONS,
      station.name,
    );

    const item = {
      ...station,
      location:
        station.location ||
        reference?.location ||
        null,
    };

    const marker = addMarker(
      map,
      item,
      COLORS.station,
      makePopup(
        station.name,
        station.address || "Dirección no disponible",
        "ORACLE · Estación activa",
        reference?.source,
      ),
    );

    list.appendChild(
      makeDataCard({
        title: station.name,
        label: "ORACLE · ESTACIÓN",
        description:
          station.address || "Dirección no disponible",
        status: marker
          ? ""
          : "Sin posición conocida",
        action: marker
          ? () => focusMarker(map, marker)
          : null,
      }),
    );
  }

  for (const hospital of hospitals) {
    const reference = findReference(
      D2_HOSPITALS,
      hospital.name,
    );

    const item = {
      ...hospital,
      location:
        hospital.location ||
        reference?.location ||
        null,
    };

    const description =
      `${hospital.address || "Dirección no disponible"} · ` +
      `Nivel ${hospital.level || "Sin datos"}`;

    const marker = addMarker(
      map,
      item,
      COLORS.hospital,
      makePopup(
        hospital.name,
        description,
        "ORACLE · Hospital",
        reference?.source,
      ),
    );

    list.appendChild(
      makeDataCard({
        title: hospital.name,
        label: "ORACLE · HOSPITAL",
        description,
        status: marker
          ? ""
          : "Sin posición conocida",
        action: marker
          ? () => focusMarker(map, marker)
          : null,
      }),
    );
  }

  showEmpty(
    list,
    "No hay hospitales ni estaciones disponibles.",
  );
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
      COLORS.emergency,
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

function renderUnits(map, units) {
  const list = byId("unit-list");
  list.replaceChildren();

  const available = units.filter((unit) => unit.status === "DISPONIBLE");
  byId("unit-pill").textContent = String(units.length);

    if (!usesSummary) {
      byId("unit-count").textContent = String(available.length);
    }
  for (const unit of units) {
    const title =
      unit.code ||
      unit.name ||
      `Unidad ${unit.id ?? ""}`;

    const isExample =
      unit.isExample === true ||
      unit.example === true ||
      String(unit.code || "").startsWith("EJEMPLO-");

    const label = isExample
      ? "EJEMPLO"
      : "ORACLE";

    const description =
      `${unit.type || "Unidad"} · ` +
      `${unit.status || "Sin estado"}`;

    const marker = addMarker(
      map,
      unit,
      COLORS.unit,
      makePopup(
        title,
        description,
        label,
      ),
    );

    list.appendChild(
      makeDataCard({
        title,
        label,
        description,
        status: marker
          ? ""
          : "Sin posición conocida",
        action: marker
          ? () => focusMarker(map, marker)
          : null,
      }),
    );
  }

  showEmpty(
    list,
    "No hay unidades disponibles.",
  );
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
  const session = await startPage();

  if (!session) {
    return;
  }


  usesSummary = hasPermission(session, "REPORTES:CONSULTAR");

  if (usesSummary) {
    refreshDashboardSummary();
    setInterval(refreshDashboardSummary, 30000);
  }


  const map = createMap();

startIncidents(
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


  byId("response-time").textContent = "Sin datos";
  byId("hospital-count").textContent = "—";
  byId("hospital-pill").textContent = "—";

  byId("source-date").textContent =
    `Fecha de revisión de fuentes: ${SOURCES_CHECKED_ON}`;

  byId("emergency-list").replaceChildren();
  byId("unit-list").replaceChildren();
  byId("hospital-list").replaceChildren();

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
    renderUnits(
      map,
      units.value,
    );
  } else {
    showFailure(
      byId("unit-list"),
      units.reason,
    );
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
    byId("hospital-pill").textContent =
      String(hospitals.value.length);
  } else {
    showFailure(
      byId("hospital-list"),
      stations.status === "rejected"
        ? stations.reason
        : hospitals.reason,
    );
  }

  // Rutas D4: demostración hasta integrar K7.
  if (map) {
    requestAnimationFrame(() => {
      map.invalidateSize();

      showRoutes(
        map,
        window.RUTAVIDA_RUTAS_DEMO,
        {
          tabs: byId("route-tabs"),
          list: byId("route-list"),
          note: byId("route-note"),
        },
      );
    });
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
