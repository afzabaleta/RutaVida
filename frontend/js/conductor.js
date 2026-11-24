import { getDriverUnit, hasPermission, reportGpsPosition } from "./api-client.js";
import { startPage } from "./page-session.js";

const EVERY_MS = 5000;
const REVERSE_GEOCODE_MS = 20000;
const ADDRESS_MOVE_METERS = 35;
const NOMINATIM_REVERSE = "https://nominatim.openstreetmap.org/reverse";
const $ = (id) => document.getElementById(id);
let watchId = null;
let timer = null;
let latest = null;
let sending = false;
let tracking = false;
let trackingVersion = 0;
let addressAt = 0;
let addressPosition = null;
let addressRequest = 0;

function status(text, state = "idle") {
  $("gps-status").textContent = text;
  $("gps-status").dataset.state = state;
}

function message(text, error = false) {
  $("gps-message").textContent = text;
  $("gps-message").classList.toggle("is-error", error);
}

function compass(degrees) {
  if (!Number.isFinite(degrees)) return "No disponible";
  const names = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"];
  return `${names[Math.round(degrees / 45) % 8]} (${Math.round(degrees)}°)`;
}

function distanceMeters(a, b) {
  if (!a || !b) return Number.POSITIVE_INFINITY;
  const radius = 6371000;
  const radians = (degrees) => degrees * Math.PI / 180;
  const dLat = radians(b.latitude - a.latitude);
  const dLng = radians(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(radians(a.latitude)) * Math.cos(radians(b.latitude))
    * Math.sin(dLng / 2) ** 2;
  return 2 * radius * Math.asin(Math.sqrt(h));
}

function displayAddress(result) {
  const address = result?.address ?? {};
  const road = String(address.road ?? address.pedestrian ?? address.footway ?? address.path ?? "")
    .replace(/^carrera\s+/i, "Cra. ")
    .replace(/^calle\s+/i, "Cl. ");
  const number = address.house_number;
  const line = [road || null, number ? `#${number}` : null].filter(Boolean).join(" ");
  const city = address.city ?? address.town ?? address.village ?? address.municipality ?? "Valledupar";
  const region = address.state ?? address.county ?? "Cesar";
  return line ? `${line}\n${city}, ${region}` : `${city}, ${region}`;
}

async function updateAddress(position) {
  const point = { latitude: position.coords.latitude, longitude: position.coords.longitude };
  const now = Date.now();
  if (now - addressAt < REVERSE_GEOCODE_MS
      && distanceMeters(addressPosition, point) < ADDRESS_MOVE_METERS) {
    return;
  }

  addressAt = now;
  addressPosition = point;
  const request = ++addressRequest;
  try {
    const query = new URLSearchParams({
      format: "jsonv2",
      lat: String(point.latitude),
      lon: String(point.longitude),
      zoom: "18",
      addressdetails: "1",
      "accept-language": "es",
    });
    const response = await fetch(`${NOMINATIM_REVERSE}?${query}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) throw new Error("No se pudo consultar la dirección.");
    const result = await response.json();
    if (request === addressRequest) {
      $("gps-address").textContent = displayAddress(result);
    }
  } catch {
    // An address is supplementary: GPS transmission must continue if the public geocoder is unavailable.
    const visibleAddress = $("gps-address").textContent.trim();
    if (request === addressRequest && (!visibleAddress || visibleAddress === "—")) {
      $("gps-address").textContent = "Dirección no disponible";
    }
  }
}

function applyTelemetry(position) {
  $("gps-speed").textContent = Number.isFinite(position.coords.speed)
    ? `${Math.round(position.coords.speed * 3.6)} km/h` : "No disponible";
  $("gps-heading").textContent = compass(position.coords.heading);
  updateAddress(position);
}

async function publish(version = trackingVersion) {
  if (!tracking || version !== trackingVersion || !latest || sending || !navigator.onLine) return;
  sending = true;
  try {
    const saved = await reportGpsPosition({
      latitude: latest.coords.latitude,
      longitude: latest.coords.longitude,
      speedMps: latest.coords.speed,
      headingDegrees: latest.coords.heading,
    });
    if (tracking && version === trackingVersion) {
      $("gps-updated").textContent = new Date(saved.updatedAt).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
      status("Transmitiendo", "online");
      message("Ubicación enviada al mapa en vivo.");
    }
  } catch (error) {
    if (tracking && version === trackingVersion) {
      status("Sin conexión", "offline");
      message(error.message, true);
    }
  } finally {
    sending = false;
  }
}

function stop() {
  tracking = false;
  trackingVersion += 1;
  if (watchId != null) navigator.geolocation.clearWatch(watchId);
  clearInterval(timer);
  watchId = null;
  timer = null;
  $("gps-start").disabled = false;
  $("gps-stop").disabled = true;
  status("Desconectado", "offline");
  message("GPS detenido.");
}

function start() {
  if (!window.isSecureContext) {
    status("Se requiere HTTPS", "error");
    message("Abre RutaVida con HTTPS en el teléfono para permitir el GPS real.", true);
    return;
  }
  if (!navigator.geolocation) {
    status("GPS no disponible", "error");
    message("Este navegador no ofrece Geolocation API.", true);
    return;
  }
  $("gps-start").disabled = true;
  $("gps-stop").disabled = false;
  tracking = true;
  const version = ++trackingVersion;
  status("Buscando señal…", "idle");
  message("Permite la ubicación precisa cuando el teléfono la solicite.");
  watchId = navigator.geolocation.watchPosition((position) => {
    latest = position;
    applyTelemetry(position);
    publish(version);
  }, (error) => {
    const text = error.code === error.PERMISSION_DENIED ? "Debes permitir la ubicación para compartirla."
      : error.code === error.POSITION_UNAVAILABLE ? "El teléfono no pudo obtener una ubicación." : "Se agotó el tiempo para obtener la ubicación.";
    status("GPS no disponible", "error");
    message(text, true);
  }, { enableHighAccuracy: true, maximumAge: 3000, timeout: 15000 });
  timer = setInterval(() => publish(version), EVERY_MS);
}

async function initialize() {
  const session = await startPage();
  if (!session) return;
  if (!hasPermission(session, "GPS:REPORTAR")) {
    status("Sin permiso", "error");
    message("Tu rol no tiene permiso para compartir GPS.", true);
    return;
  }
  try {
    const unit = await getDriverUnit();
    $("driver-unit").textContent = `${unit.unitCode}${unit.plate ? ` · ${unit.plate}` : ""}`;
    if (unit.updatedAt) $("gps-updated").textContent = new Date(unit.updatedAt).toLocaleString("es-CO");
  } catch (error) {
    status("Sin unidad asignada", "error");
    message(error.message, true);
    $("gps-start").disabled = true;
  }
  $("gps-start").addEventListener("click", start);
  $("gps-stop").addEventListener("click", stop);
  window.addEventListener("online", () => { if (tracking) { status("Buscando señal…", "idle"); publish(); } });
  window.addEventListener("offline", () => status("Sin conexión", "offline"));
  window.addEventListener("pagehide", stop);
}

initialize();
