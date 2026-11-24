import { getDriverUnit, hasPermission, reportGpsPosition } from "./api-client.js";
import { startPage } from "./page-session.js";

const EVERY_MS = 5000;
const $ = (id) => document.getElementById(id);
let watchId = null;
let timer = null;
let latest = null;
let sending = false;

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

function applyTelemetry(position) {
  $("gps-speed").textContent = Number.isFinite(position.coords.speed)
    ? `${Math.round(position.coords.speed * 3.6)} km/h` : "No disponible";
  $("gps-heading").textContent = compass(position.coords.heading);
}

async function publish() {
  if (!latest || sending || !navigator.onLine) return;
  sending = true;
  try {
    const saved = await reportGpsPosition({
      latitude: latest.coords.latitude,
      longitude: latest.coords.longitude,
      speedMps: latest.coords.speed,
      headingDegrees: latest.coords.heading,
    });
    $("gps-updated").textContent = new Date(saved.updatedAt).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    status("Transmitiendo", "online");
    message("Ubicación enviada al mapa en vivo.");
  } catch (error) {
    status("Sin conexión", "offline");
    message(error.message, true);
  } finally {
    sending = false;
  }
}

function stop() {
  if (watchId != null) navigator.geolocation.clearWatch(watchId);
  clearInterval(timer);
  watchId = null;
  timer = null;
  $("gps-start").disabled = false;
  $("gps-stop").disabled = true;
  status("Detenido");
  message("El teléfono dejó de compartir la ubicación.");
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
  status("Buscando señal…");
  message("Permite la ubicación precisa cuando el teléfono la solicite.");
  watchId = navigator.geolocation.watchPosition((position) => {
    latest = position;
    applyTelemetry(position);
    publish();
  }, (error) => {
    const text = error.code === error.PERMISSION_DENIED ? "Debes permitir la ubicación para compartirla."
      : error.code === error.POSITION_UNAVAILABLE ? "El teléfono no pudo obtener una ubicación." : "Se agotó el tiempo para obtener la ubicación.";
    status("GPS no disponible", "error");
    message(text, true);
  }, { enableHighAccuracy: true, maximumAge: 3000, timeout: 15000 });
  timer = setInterval(publish, EVERY_MS);
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
  window.addEventListener("online", () => { if (watchId != null) { status("Transmitiendo", "online"); publish(); } });
  window.addEventListener("offline", () => status("Sin conexión", "offline"));
  window.addEventListener("pagehide", stop);
}

initialize();
