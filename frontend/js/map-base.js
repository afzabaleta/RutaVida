
import { ROAD_NETWORK_ZONE, STATIONS, HOSPITALS } from "./valledupar-data.js";

const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

export const COLORS = {
  station: "#4bd6a0",
  hospital: "#79adff",
  emergency: "#ff6674",
  unit: "#f6b94b",
};

export const DEMO_POINT = [10.467172, -73.265099];

export const byId = (id) => document.getElementById(id);

export function addText(parent, tag, text, className = "") {
  const element = document.createElement(tag);
  element.textContent = String(text ?? "");

  if (className) {
    element.className = className;
  }

  parent.appendChild(element);
  return element;
}

export function makeTag(text, isReal = false) {
  const tag = document.createElement("span");
  tag.className = isReal ? "data-tag real-tag" : "data-tag";
  tag.textContent = text;
  return tag;
}

export function zoneBounds() {
  return L.latLngBounds(
    [ROAD_NETWORK_ZONE.south, ROAD_NETWORK_ZONE.west],
    [ROAD_NETWORK_ZONE.north, ROAD_NETWORK_ZONE.east],
  );
}

export function insideZone(latitude, longitude) {
  return latitude >= ROAD_NETWORK_ZONE.south
    && latitude <= ROAD_NETWORK_ZONE.north
    && longitude >= ROAD_NETWORK_ZONE.west
    && longitude <= ROAD_NETWORK_ZONE.east;
}

export function straightMeters(a, b) {
  const radius = 6371000;
  const rad = (degrees) => (degrees * Math.PI) / 180;

  const dLat = rad(b[0] - a[0]);
  const dLon = rad(b[1] - a[1]);

  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(rad(a[0]))
    * Math.cos(rad(b[0]))
    * Math.sin(dLon / 2) ** 2;

  return 2 * radius * Math.asin(Math.sqrt(h));
}

export function circleMarker(map, location, color, popupText) {
  const marker = L.circleMarker(location, {
    radius: 8,
    color: "#101923",
    weight: 2,
    fillColor: color,
    fillOpacity: 1,
  }).addTo(map);

  if (popupText) {
    marker.bindPopup(popupText);
  }

  return marker;
}

export function createBaseMap(elementId) {
  const element = byId(elementId);

  if (typeof L === "undefined") {
    element.textContent =
      "No se pudo cargar el mapa. Comprueba la conexión a internet y recarga la página.";
    return null;
  }

  const zone = zoneBounds();

  const map = L.map(elementId, {
    maxBounds: zone.pad(0.5),
    maxBoundsViscosity: 0.5,
  });

  L.tileLayer(TILE_URL, {
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a>',
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

  for (const station of STATIONS) {
    circleMarker(
      map,
      station.location,
      COLORS.station,
      `${station.name} (REAL, D2)`,
    );
  }

  for (const hospital of HOSPITALS) {
    circleMarker(
      map,
      hospital.location,
      COLORS.hospital,
      `${hospital.name} (REAL, D2)`,
    );
  }

  return map;
}
