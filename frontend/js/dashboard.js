import {
  STATIONS,
  HOSPITALS,
  ROAD_NETWORK_ZONE,
  SOURCES_CHECKED_ON,
} from "./valledupar-data.js";

import {
  EXAMPLE_EMERGENCIES,
  EXAMPLE_UNITS,
  EXAMPLE_USER,
} from "./example-data.js";

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
  if (className) element.className = className;
  parent.appendChild(element);
  return element;
}

function makeTag(text, isReal = false) {
  const tag = document.createElement("span");
  tag.className = isReal ? "data-tag real-tag" : "data-tag";
  tag.textContent = text;
  return tag;
}

function makeDataCard({ title, label, description, status, statusClass, isReal, action }) {
  const card = document.createElement("article");
  card.className = "data-card";

  const top = document.createElement("div");
  top.className = "data-card-top";
  addText(top, "h3", title);
  top.appendChild(makeTag(label, isReal));
  card.appendChild(top);

  if (description) addText(card, "p", description);

  if (status) {
    addText(card, "p", status, `status-text ${statusClass || ""}`.trim());
  }

  if (action) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "map-action";
    button.textContent = "Ver en el mapa";
    button.addEventListener("click", action);
    card.appendChild(button);
  }

  return card;
}

function makePopup({ title, description, label, isReal, source, sourceText = "Consultar fuente" }) {
  const popup = document.createElement("div");
  popup.className = "map-popup";
  addText(popup, "strong", title);
  addText(popup, "p", description);
  addText(popup, "span", label, `map-popup-label ${isReal ? "real-label" : ""}`.trim());

  if (source) {
    const link = document.createElement("a");
    link.href = source;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = sourceText;
    popup.appendChild(link);
  }

  return popup;
}

function addMarker(map, item, color, popupContent) {
  const marker = L.circleMarker(item.location, {
    radius: 8,
    color: "#101923",
    weight: 2,
    fillColor: color,
    fillOpacity: 1,
  }).addTo(map);

  marker.bindPopup(popupContent);
  return marker;
}

function focusMarker(map, marker) {
  map.setView(marker.getLatLng(), Math.max(map.getZoom(), 15), { animate: true });
  marker.openPopup();
}

function initializeDashboard() {
  if (typeof L === "undefined") {
    const mapElement = byId("map");
    mapElement.textContent = "No se pudo cargar Leaflet. Comprueba la conexión a internet y recarga la página.";
    return;
  }

  byId("user-name").textContent = EXAMPLE_USER.name;
  byId("user-role").textContent = EXAMPLE_USER.role;

  byId("emergency-count").textContent = String(EXAMPLE_EMERGENCIES.length);
  byId("unit-count").textContent = String(
    EXAMPLE_UNITS.filter((unit) => unit.status === "DISPONIBLE").length,
  );
  byId("hospital-count").textContent = String(HOSPITALS.length);
  byId("response-time").textContent = "Sin datos";

  byId("emergency-pill").textContent = String(EXAMPLE_EMERGENCIES.length);
  byId("unit-pill").textContent = String(EXAMPLE_UNITS.length);
  byId("hospital-pill").textContent = String(HOSPITALS.length);
  byId("source-date").textContent = `Fecha de revisión de fuentes: ${SOURCES_CHECKED_ON}`;

  const zone = L.latLngBounds(
    [ROAD_NETWORK_ZONE.south, ROAD_NETWORK_ZONE.west],
    [ROAD_NETWORK_ZONE.north, ROAD_NETWORK_ZONE.east],
  );

  const map = L.map("map", {
    maxBounds: zone.pad(0.5),
    maxBoundsViscosity: 0.5,
  });

  L.tileLayer(TILE_URL, {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a>',
    maxZoom: 19,
  }).addTo(map);

  map.fitBounds(zone);

  L.rectangle(zone, {
    color: COLORS.hospital,
    weight: 2,
    dashArray: "6 6",
    fill: false,
    interactive: false,
  }).addTo(map);

  const emergencyList = byId("emergency-list");
  const unitList = byId("unit-list");
  const hospitalList = byId("hospital-list");
  emergencyList.replaceChildren();
  unitList.replaceChildren();
  hospitalList.replaceChildren();

  for (const station of STATIONS) {
    const marker = addMarker(
      map,
      station,
      COLORS.station,
      makePopup({
        title: station.name,
        description: station.address,
        label: "Real (D2) · Ubicación referencial",
        isReal: true,
        source: station.source,
      }),
    );

    const card = makeDataCard({
      title: station.name,
      label: "REAL (D2)",
      description: station.address,
      isReal: true,
      action: () => focusMarker(map, marker),
    });
    hospitalList.insertBefore(card, hospitalList.firstChild);
  }

  for (const hospital of HOSPITALS) {
    const marker = addMarker(
      map,
      hospital,
      COLORS.hospital,
      makePopup({
        title: hospital.name,
        description: `${hospital.address}. Nivel registrado: ${hospital.level}.`,
        label: "Real (D2) · Verificar datos",
        isReal: true,
        source: hospital.source,
      }),
    );

    const card = makeDataCard({
      title: hospital.name,
      label: "REAL (D2)",
      description: `${hospital.address} · Nivel ${hospital.level}.${hospital.note ? ` ${hospital.note}.` : ""}`,
      isReal: true,
      action: () => focusMarker(map, marker),
    });
    hospitalList.appendChild(card);
  }

  for (const emergency of EXAMPLE_EMERGENCIES) {
    const marker = addMarker(
      map,
      emergency,
      COLORS.emergency,
      makePopup({
        title: `${emergency.code} · ${emergency.type}`,
        description: `${emergency.place}. Severidad: ${emergency.severity}. Estado: ${emergency.status}.`,
        label: "EJEMPLO · No es un reporte real",
        isReal: false,
      }),
    );

    emergencyList.appendChild(
      makeDataCard({
        title: `${emergency.code} · ${emergency.type.replaceAll("_", " ")}`,
        label: "EJEMPLO",
        description: `${emergency.place} · Severidad: ${emergency.severity}`,
        status: emergency.status.replaceAll("_", " "),
        statusClass: emergency.status === "REPORTADA" ? "status-critical" : "status-assigned",
        action: () => focusMarker(map, marker),
      }),
    );
  }

  for (const unit of EXAMPLE_UNITS) {
    const marker = addMarker(
      map,
      unit,
      COLORS.unit,
      makePopup({
        title: `${unit.code} · ${unit.type}`,
        description: `${unit.place}. Estado: ${unit.status}.`,
        label: "EJEMPLO · No es una unidad operativa real",
        isReal: false,
      }),
    );

    unitList.appendChild(
      makeDataCard({
        title: `${unit.code} · ${unit.type}`,
        label: "EJEMPLO",
        description: unit.place,
        status: unit.status,
        statusClass: unit.status === "DISPONIBLE" ? "status-available" : "",
        action: () => focusMarker(map, marker),
      }),
    );
  }

  requestAnimationFrame(() => map.invalidateSize());
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initializeDashboard, { once: true });
} else {
  initializeDashboard();
}
