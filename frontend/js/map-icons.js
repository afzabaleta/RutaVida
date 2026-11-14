// Map icons (D7): each unit is drawn as its kind of vehicle (ambulance, fire truck or police
// patrol) and the ring around it shows its status. Hospitals, stations and emergencies have
// their own icon too. The drawings are simple original SVG, made for RutaVida.

const SIZE = 34;

/** Units are drawn above hospitals, stations and emergencies (Leaflet zIndexOffset). */
export const UNIT_Z = 1000;

/** Distance in pixels between units parked at the same point (for example, at their station). */
const SPREAD = 34;

/** Ring of a unit by its operational status. */
const STATUS_RING = {
  DISPONIBLE: "#22c55e",
  OCUPADO: "#f59e0b",
  MANTENIMIENTO: "#94a3b8",
  FUERA_SERVICIO: "#94a3b8",
};

const RED = "#e5484d";
const BLUE = "#3b82f6";
const TIRE = "#1f2a37";

/** Van seen from the side: body, cab window and two wheels. */
function van(badge) {
  return `
    <path d="M8 25V17.5a2 2 0 0 1 2-2h14l5 4.2h2.2a2 2 0 0 1 2 2V25z" fill="#fff"/>
    <path d="M25 16.9l3.6 2.8H25z" fill="${badge}"/>
    <circle cx="13" cy="25.5" r="2.7" fill="${TIRE}" stroke="#fff" stroke-width="1.2"/>
    <circle cx="28" cy="25.5" r="2.7" fill="${TIRE}" stroke="#fff" stroke-width="1.2"/>`;
}

const GLYPHS = {
  AMBULANCIA: {
    badge: "#c81e2c",
    label: "Ambulancia",
    body: (badge) => `
      <rect x="11" y="12.4" width="3.8" height="2.4" rx=".8" fill="${RED}" stroke="#fff" stroke-width=".6"/>
      <rect x="14.8" y="12.4" width="3.8" height="2.4" rx=".8" fill="${BLUE}" stroke="#fff" stroke-width=".6"/>
      ${van(badge)}
      <rect x="15" y="17" width="2.4" height="6.6" fill="${RED}"/>
      <rect x="12.9" y="19.1" width="6.6" height="2.4" fill="${RED}"/>`,
  },
  BOMBERO: {
    badge: "#b45309",
    label: "Bomberos",
    body: (badge) => `
      <rect x="8.5" y="12.6" width="15" height="1.4" fill="#fff"/>
      <path d="M10 12.6v2.6M13 12.6v2.6M16 12.6v2.6M19 12.6v2.6M22 12.6v2.6" stroke="#fff" stroke-width="1"/>
      ${van(badge)}
      <rect x="10.5" y="18.2" width="11" height="1.6" fill="${RED}"/>`,
  },
  POLICIA: {
    badge: "#1d4ed8",
    label: "Patrulla",
    body: () => `
      <rect x="15" y="12.6" width="3.4" height="2.2" rx=".8" fill="${RED}" stroke="#fff" stroke-width=".6"/>
      <rect x="18.4" y="12.6" width="3.4" height="2.2" rx=".8" fill="${BLUE}" stroke="#fff" stroke-width=".6"/>
      <path d="M7 25v-3.6l3.4-1.2 3.4-4.2h10.4l4.4 4.2h2.6a2 2 0 0 1 2 2V25z" fill="#fff"/>
      <path d="M15.2 16.9h3.6v3.2h-6.2zM20.2 16.9h3.4l3.2 3.2h-6.6z" fill="#1d4ed8"/>
      <circle cx="12.5" cy="25.5" r="2.7" fill="${TIRE}" stroke="#fff" stroke-width="1.2"/>
      <circle cx="27.5" cy="25.5" r="2.7" fill="${TIRE}" stroke="#fff" stroke-width="1.2"/>`,
  },
  UNIDAD: {
    badge: "#475569",
    label: "Unidad",
    body: (badge) => van(badge),
  },
};

const PLACES = {
  hospital: `
    <rect x="4" y="4" width="32" height="32" rx="8" fill="#2563eb" stroke="#fff" stroke-width="2.5"/>
    <path d="M14 11v18M26 11v18M14 20h12" stroke="#fff" stroke-width="4" stroke-linecap="round"/>`,
  station: `
    <rect x="4" y="4" width="32" height="32" rx="8" fill="#0f9f6e" stroke="#fff" stroke-width="2.5"/>
    <path d="M10 20l10-8.5L30 20" fill="none" stroke="#fff" stroke-width="3" stroke-linejoin="round" stroke-linecap="round"/>
    <path d="M13 19v10h14V19" fill="none" stroke="#fff" stroke-width="3" stroke-linejoin="round"/>
    <rect x="18" y="22.5" width="4" height="6.5" fill="#fff"/>`,
  emergency: `
    <circle cx="20" cy="20" r="16" fill="${RED}" stroke="#fff" stroke-width="2.5"/>
    <path d="M20 10.5v11" stroke="#fff" stroke-width="4" stroke-linecap="round"/>
    <circle cx="20" cy="28" r="2.5" fill="#fff"/>`,
};

function glyphOf(vehicleType) {
  return GLYPHS[String(vehicleType ?? "").toUpperCase()] ?? GLYPHS.UNIDAD;
}

/** SVG of a unit: a round badge with its vehicle, and a ring with the color of its status. */
export function unitSvg(vehicleType, status) {
  const glyph = glyphOf(vehicleType);
  const ring = STATUS_RING[String(status ?? "").toUpperCase()] ?? "#fff";
  return `<svg viewBox="0 0 40 40" width="${SIZE}" height="${SIZE}" aria-hidden="true">
    <circle cx="20" cy="20" r="17" fill="${glyph.badge}" stroke="${ring}" stroke-width="3"/>
    ${glyph.body(glyph.badge)}
  </svg>`;
}

/** SVG of a hospital, a station or an emergency. */
export function placeSvg(kind) {
  return `<svg viewBox="0 0 40 40" width="${SIZE}" height="${SIZE}" aria-hidden="true">${PLACES[kind] ?? PLACES.emergency}</svg>`;
}

function divIcon(html, extraClass = "", shift = [0, 0]) {
  return L.divIcon({
    html,
    className: `rv-map-icon ${extraClass}`.trim(),
    iconSize: [SIZE, SIZE],
    iconAnchor: [SIZE / 2 - shift[0], SIZE / 2 - shift[1]],
    popupAnchor: [shift[0], shift[1] - SIZE / 2],
  });
}

/**
 * Pixel shift of the unit number {@code slot} among those at the same point: the first stays
 * on the point and the others go around it, so no unit hides another one or its station.
 */
function shiftOf(slot) {
  if (!slot) {
    return [0, 0];
  }
  const angle = (slot - 1) * (Math.PI / 3) - Math.PI / 2;
  return [Math.round(Math.cos(angle) * SPREAD), Math.round(Math.sin(angle) * SPREAD)];
}

/**
 * Leaflet icon of a unit (AMBULANCIA, BOMBERO, POLICIA) with the ring of its status.
 * {@code slot} is its place among the units at the same point (see unitSlots).
 */
export function unitIcon(vehicleType, status, slot = 0) {
  return divIcon(unitSvg(vehicleType, status), "", shiftOf(slot));
}

/**
 * For each item with a [lat, lng] location, its place among the items at the same point:
 * 1, 2, 3... so every unit gets its own spot around the point. A unit alone at its point,
 * or without location, gets 0 and is drawn exactly where it is.
 */
export function unitSlots(items, locationOf = (item) => item.location) {
  const keyOf = (item) => {
    const location = locationOf(item);
    return Array.isArray(location) ? `${location[0].toFixed(5)},${location[1].toFixed(5)}` : null;
  };
  const total = new Map();
  for (const item of items) {
    const key = keyOf(item);
    if (key) {
      total.set(key, (total.get(key) ?? 0) + 1);
    }
  }
  const used = new Map();
  return items.map((item) => {
    const key = keyOf(item);
    if (!key || total.get(key) < 2) {
      return 0;
    }
    const slot = (used.get(key) ?? 0) + 1;
    used.set(key, slot);
    return slot;
  });
}

/** Leaflet icon of a place: "hospital", "station" or "emergency" (the emergency pulses). */
export function placeIcon(kind) {
  return divIcon(placeSvg(kind), kind === "emergency" ? "rv-pulse" : "");
}

/** Spanish name of the kind of vehicle, for popups. */
export function vehicleLabel(vehicleType) {
  return glyphOf(vehicleType).label;
}

/** Adds a marker with an icon to the map; the popup is plain text. Units pass UNIT_Z. */
export function iconMarker(map, location, icon, popupText, zIndexOffset = 0) {
  const marker = L.marker(location, { icon, keyboard: false, zIndexOffset }).addTo(map);
  if (popupText) {
    marker.bindPopup(popupText);
  }
  return marker;
}

/**
 * Draws the small icons of a legend: every element with data-icon="hospital", "station",
 * "emergency", "AMBULANCIA", "BOMBERO" or "POLICIA" gets its SVG.
 */
export function paintLegend(root = document) {
  for (const element of root.querySelectorAll("[data-icon]")) {
    const kind = element.dataset.icon;
    element.innerHTML = PLACES[kind] ? placeSvg(kind) : unitSvg(kind, "DISPONIBLE");
  }
}
