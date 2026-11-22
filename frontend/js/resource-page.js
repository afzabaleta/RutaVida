// Shared pieces of the Hospitales and Unidades pages: a map of Valledupar next to a list of
// cards, where each card centers the map on its marker. Only reads; the changes are made in
// Administración.
import { ROAD_NETWORK_ZONE } from "./valledupar-data.js";

const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

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

/** Map of Valledupar framed on the road network zone, or null when Leaflet did not load. */
export function createResourceMap(elementId) {
  const element = byId(elementId);
  if (typeof L === "undefined") {
    element.textContent = "No se pudo cargar el mapa. Recarga la página.";
    return null;
  }
  const { south, west, north, east } = ROAD_NETWORK_ZONE;
  const zone = L.latLngBounds([south, west], [north, east]);
  const map = L.map(elementId, { maxBounds: zone.pad(0.5), maxBoundsViscosity: 0.5 });
  L.tileLayer(TILE_URL, { attribution: "&copy; OpenStreetMap contributors", maxZoom: 19 }).addTo(map);
  map.fitBounds(zone);
  return map;
}

/** Leaflet marker with a popup of a title and lines of text, or null without a valid location. */
export function placeMarker(map, location, icon, title, lines = []) {
  if (!map || !Array.isArray(location) || !location.every(Number.isFinite)) {
    return null;
  }
  const popup = document.createElement("div");
  const heading = addText(popup, "strong", title);
  heading.style.display = "block";
  for (const line of lines.filter(Boolean)) {
    addText(popup, "p", line);
  }
  return L.marker(location, { icon, keyboard: false }).addTo(map).bindPopup(popup);
}

/** Centers the map on the marker and opens its popup. On a phone the map is above: scroll to it. */
export function focusOn(map, marker) {
  if (!map || !marker) {
    return;
  }
  map.setView(marker.getLatLng(), Math.max(map.getZoom(), 16));
  marker.openPopup();
  if (window.matchMedia("(max-width: 899.98px)").matches) {
    map.getContainer().scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

/**
 * A card of the list. lines are short texts (or nodes) under the title; a click (or Enter)
 * on the card runs onSelect, if there is one, and hint tells so.
 */
export function resourceCard({
  title, tag, tagIsReal = true, lines = [], status = null, onSelect = null, hint = "Clic para verlo en el mapa",
}) {
  const card = document.createElement("article");
  card.className = "data-card resource-card";

  const top = document.createElement("div");
  top.className = "data-card-top";
  addText(top, "strong", title);
  if (tag) {
    addText(top, "span", tag, tagIsReal ? "data-tag real-tag" : "data-tag");
  }
  card.appendChild(top);

  if (status) {
    addText(card, "span", status.text, `resource-status ${status.className ?? ""}`.trim());
  }
  for (const line of lines.filter(Boolean)) {
    if (line instanceof Node) {
      card.appendChild(line);
    } else {
      addText(card, "p", line);
    }
  }

  if (onSelect) {
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.setAttribute("aria-label", `Ver ${title} en el mapa`);
    addText(card, "small", hint, "muted");
    card.addEventListener("click", onSelect);
    card.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        onSelect();
      }
    });
  }
  return card;
}

/** Replaces the content of a list with one message (loading, empty or error). */
export function listMessage(list, text, isError = false) {
  list.replaceChildren();
  addText(list, "p", text, isError ? "muted resource-error" : "muted");
}

/** Case and accent insensitive text, for the search box. */
export function plain(text) {
  return String(text ?? "").normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}
