import {
  confirmDriverArrival, finishDriverAttention, getDriverMission, getDriverUnit, getTracking,
  hasPermission, listHospitals, listMissionRoutes, listStations, reportGpsPosition,
  startDriverGpsSession, stopDriverGpsSession,
} from "./api-client.js";
import { startPage } from "./page-session.js";

const EVERY_MS = 5000;
const REVERSE_GEOCODE_MS = 20000;
const ADDRESS_MOVE_METERS = 35;
const NOMINATIM_REVERSE = "https://nominatim.openstreetmap.org/reverse";
const VALLEDUPAR = [10.4631, -73.2532];
const $ = (id) => document.getElementById(id);

let watchId = null;
let timer = null;
let latest = null;
let sending = false;
let tracking = false;
let trackingVersion = 0;
let gpsSessionRecorded = false;
let addressAt = 0;
let addressPosition = null;
let addressRequest = 0;
let canOperate = false;
let currentMission = null;
let liveMission = null;
let missionRefreshing = false;
let lastRouteKey = null;
let map = null;
let ownMarker = null;
let emergencyMarker = null;
let routeLine = null;
let hospitals = [];
let mapFitted = false;
let completedMission = null;

function status(text, state = "idle") {
  $("gps-status").textContent = text;
  $("gps-status").dataset.state = state;
  $("driver-map-state").textContent = state === "online" ? "GPS en vivo" : text === "Desconectado" ? "GPS detenido" : text;
  $("driver-map-state").classList.toggle("is-live", state === "online");
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

function distanceKm(a, b) {
  return distanceMeters({ latitude: a[0], longitude: a[1] }, { latitude: b[0], longitude: b[1] }) / 1000;
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
  if (now - addressAt < REVERSE_GEOCODE_MS && distanceMeters(addressPosition, point) < ADDRESS_MOVE_METERS) return;

  addressAt = now;
  addressPosition = point;
  const request = ++addressRequest;
  try {
    const query = new URLSearchParams({ format: "jsonv2", lat: String(point.latitude), lon: String(point.longitude), zoom: "18", addressdetails: "1", "accept-language": "es" });
    const response = await fetch(`${NOMINATIM_REVERSE}?${query}`, { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error("No se pudo consultar la dirección.");
    const result = await response.json();
    if (request === addressRequest) $("gps-address").textContent = displayAddress(result);
  } catch {
    const visibleAddress = $("gps-address").textContent.trim();
    if (request === addressRequest && (!visibleAddress || visibleAddress === "—")) $("gps-address").textContent = "Dirección no disponible";
  }
}

function driverIcon() {
  return L.divIcon({ html: '<div class="driver-map-unit"><span>🚑</span></div>', className: "", iconSize: [42, 42], iconAnchor: [21, 39] });
}

function emergencyIcon() {
  return L.divIcon({ html: '<div class="driver-map-emergency">!</div>', className: "", iconSize: [33, 33], iconAnchor: [16, 16] });
}

function createMap() {
  if (typeof L === "undefined") {
    $("driver-map").textContent = "No se pudo cargar el mapa.";
    return;
  }
  map = L.map("driver-map", { zoomControl: true }).setView(VALLEDUPAR, 13);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19 }).addTo(map);
}

function updateOwnMarker(location, focus = false) {
  if (!map || !location) return;
  if (!ownMarker) ownMarker = L.marker(location, { icon: driverIcon(), zIndexOffset: 1000 }).addTo(map).bindTooltip("Tu ambulancia", { direction: "top" });
  else ownMarker.setLatLng(location);
  if (focus || !mapFitted) {
    map.setView(location, Math.max(map.getZoom(), 15));
    mapFitted = true;
  }
}

function setEmergencyMarker(mission) {
  if (!map) return;
  if (!mission?.emergencyLocation) {
    emergencyMarker?.remove();
    emergencyMarker = null;
    return;
  }
  const label = `Emergencia #${mission.emergencyId}${mission.emergencyAddress ? ` · ${mission.emergencyAddress}` : ""}`;
  if (!emergencyMarker) emergencyMarker = L.marker([mission.emergencyLocation.latitude, mission.emergencyLocation.longitude], { icon: emergencyIcon() }).addTo(map).bindTooltip(label, { direction: "top" });
  else emergencyMarker.setLatLng([mission.emergencyLocation.latitude, mission.emergencyLocation.longitude]).bindTooltip(label);
}

function clearRoute() {
  routeLine?.remove();
  routeLine = null;
  lastRouteKey = null;
}

async function drawRoute(mission) {
  if (!map || !mission?.assignmentId || mission.activeRouteId == null) {
    clearRoute();
    return;
  }
  const key = `${mission.assignmentId}:${mission.routesVersion}`;
  if (key === lastRouteKey) return;
  try {
    const routes = await listMissionRoutes(mission.assignmentId);
    const active = routes.find((route) => route.status === "ACTIVA");
    routeLine?.remove();
    routeLine = active?.path?.length > 1 ? L.polyline(active.path, { color: "#3d8bff", weight: 6, opacity: .88 }).addTo(map) : null;
    lastRouteKey = key;
  } catch (error) {
    console.warn("No se pudo mostrar la ruta activa", error);
  }
}

async function loadResources() {
  if (!map) return;
  try {
    const [loadedHospitals, stations] = await Promise.all([listHospitals(), listStations()]);
    hospitals = loadedHospitals.filter((hospital) => hospital.location);
    for (const hospital of hospitals) {
      L.circleMarker(hospital.location, { radius: 7, color: "#fff", weight: 2, fillColor: "#3b82f6", fillOpacity: 1 }).addTo(map).bindTooltip(hospital.name);
    }
    for (const station of stations.filter((station) => station.location)) {
      L.circleMarker(station.location, { radius: 6, color: "#fff", weight: 2, fillColor: "#18a865", fillOpacity: 1 }).addTo(map).bindTooltip(station.name);
    }
  } catch (error) {
    console.warn("No se pudieron cargar todos los recursos operativos", error);
  }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—";
}

function formatDuration(start, end) {
  if (!start || !end) return "—";
  const seconds = Math.max(0, Math.round((new Date(end) - new Date(start)) / 1000));
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  return hours > 0 ? `${hours} h ${minutes % 60} min` : `${minutes} min ${seconds % 60} s`;
}

function renderMission(mission) {
  currentMission = mission;
  const active = Boolean(mission?.assignmentId) && mission.status !== "FINALIZADA" && mission.status !== "CANCELADA";
  const status = mission?.status;
  $("mission-arrival").disabled = !canOperate || !["ASIGNADA", "EN_RUTA"].includes(status);
  $("mission-finish").disabled = !canOperate || status !== "EN_SITIO";
  $("driver-emergency").hidden = !mission?.emergencyId;
  if (mission?.emergencyId) {
    $("driver-emergency-detail").textContent = `EMG-${mission.emergencyId}${mission.emergencyAddress ? ` · ${mission.emergencyAddress}` : ""}`;
  }
  const hasRoute = active && Number.isFinite(mission?.remainingDistanceKm);
  $("driver-route").hidden = !hasRoute;
  if (hasRoute) {
    $("route-distance").textContent = `${mission.remainingDistanceKm.toFixed(2)} km`;
    $("route-eta").textContent = Number.isFinite(mission.etaMinutes) ? `${Math.ceil(mission.etaMinutes)} min` : "—";
  }
  const finished = mission?.status === "FINALIZADA" && mission.finishedAt;
  $("driver-finished").hidden = !finished;
  if (finished) {
    $("finished-arrival").textContent = formatTime(mission.arrivedAt);
    $("finished-at").textContent = formatTime(mission.finishedAt);
    $("finished-duration").textContent = formatDuration(mission.arrivedAt, mission.finishedAt);
  }
  setEmergencyMarker(mission);
  drawRoute(mission);
}

async function refreshMission() {
  if (missionRefreshing) return;
  missionRefreshing = true;
  try {
    const [mission, trackingSnapshot] = await Promise.all([getDriverMission(), getTracking()]);
    const live = mission.assignmentId
      ? trackingSnapshot.missions?.find((item) => item.assignmentId === mission.assignmentId)
      : null;
    liveMission = live ?? null;
    const combined = live ? { ...mission, ...live } : mission;
    if (combined.assignmentId) completedMission = null;
    if (!combined.assignmentId && completedMission) {
      renderMission(completedMission);
      return;
    }
    renderMission(combined);
    const point = combined.unit?.location;
    if (point) updateOwnMarker([point.latitude, point.longitude]);
  } catch (error) {
    console.warn("No se pudo actualizar la misión del conductor", error);
  } finally {
    missionRefreshing = false;
  }
}

function renderNearbyHospitals() {
  const list = $("nearby-hospitals-list");
  const point = latest ? [latest.coords.latitude, latest.coords.longitude]
    : currentMission?.unit?.location ? [currentMission.unit.location.latitude, currentMission.unit.location.longitude] : null;
  list.replaceChildren();
  if (!point) {
    const item = document.createElement("li");
    item.textContent = "Inicia el GPS para ordenar hospitales por cercanía.";
    list.append(item);
    return;
  }
  hospitals.map((hospital) => ({ ...hospital, km: distanceKm(point, hospital.location) }))
    .sort((a, b) => a.km - b.km).slice(0, 5).forEach((hospital) => {
      const item = document.createElement("li");
      const text = document.createElement("span");
      const name = document.createElement("strong");
      name.textContent = hospital.name;
      const detail = document.createElement("small");
      detail.textContent = `${hospital.km.toFixed(1)} km · ${Math.max(1, Math.round(hospital.km / 35 * 60))} min aprox.`;
      text.append(name, detail);
      const show = document.createElement("button");
      show.type = "button";
      show.textContent = "Ver mapa";
      show.addEventListener("click", () => map?.setView(hospital.location, 16));
      item.append(text, show);
      list.append(item);
    });
}

function applyTelemetry(position) {
  $("gps-speed").textContent = Number.isFinite(position.coords.speed) ? `${Math.round(position.coords.speed * 3.6)} km/h` : "No disponible";
  updateAddress(position);
  updateOwnMarker([position.coords.latitude, position.coords.longitude]);
  if (!$("nearby-hospitals-list").hidden) renderNearbyHospitals();
}

async function publish(version = trackingVersion) {
  if (!tracking || version !== trackingVersion || !latest || sending || !navigator.onLine) return;
  sending = true;
  try {
    const saved = await reportGpsPosition({ latitude: latest.coords.latitude, longitude: latest.coords.longitude, speedMps: latest.coords.speed, headingDegrees: latest.coords.heading });
    if (tracking && version === trackingVersion) {
      $("gps-updated").textContent = formatTime(saved.updatedAt);
      status("Transmitiendo", "online");
      message("Ubicación enviada al mapa en vivo.");
      if (!gpsSessionRecorded && canOperate) {
        // A failure to register the audit event must not pretend that the confirmed GPS
        // position was lost; it was already stored by reportGpsPosition above.
        try {
          const operation = await startDriverGpsSession();
          gpsSessionRecorded = true;
          renderMission(operation);
        } catch (error) {
          console.warn("No se pudo registrar el inicio de GPS", error);
        }
      }
      refreshMission();
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

async function stop() {
  tracking = false;
  trackingVersion += 1;
  if (watchId != null) navigator.geolocation.clearWatch(watchId);
  clearInterval(timer);
  watchId = null;
  timer = null;
  $("gps-start").disabled = false;
  $("gps-stop").disabled = true;
  status("Desconectado", "offline");
  $("gps-updated").textContent = formatTime(new Date());
  message("GPS detenido.");
  if (gpsSessionRecorded && canOperate) {
    try { await stopDriverGpsSession(); } catch (error) { console.warn("No se pudo registrar la detención GPS", error); }
  }
  gpsSessionRecorded = false;
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

async function confirmArrival() {
  try {
    const mission = await confirmDriverArrival();
    renderMission(mission);
    message("Llegada registrada. El GPS continúa transmitiendo.");
    refreshMission();
  } catch (error) {
    message(error.message, true);
  }
}

function showFinishModal(show) {
  $("finish-modal").hidden = !show;
  if (show) $("finish-confirm").focus();
}

async function finishAttention() {
  $("finish-confirm").disabled = true;
  try {
    const mission = await finishDriverAttention();
    completedMission = mission;
    renderMission(mission);
    message("Atención finalizada. La unidad está disponible y el GPS sigue activo.");
    showFinishModal(false);
    refreshMission();
  } catch (error) {
    message(error.message, true);
  } finally {
    $("finish-confirm").disabled = false;
  }
}

async function initialize() {
  const session = await startPage();
  if (!session) return;
  if (!hasPermission(session, "GPS:REPORTAR")) {
    status("Sin permiso", "error");
    message("Tu rol no tiene permiso para compartir GPS.", true);
    return;
  }
  canOperate = hasPermission(session, "GPS:OPERAR");
  createMap();
  try {
    const unit = await getDriverUnit();
    $("driver-unit").textContent = `${unit.unitCode}${unit.plate ? ` · ${unit.plate}` : ""}`;
    if (unit.location) updateOwnMarker([unit.location.latitude, unit.location.longitude], true);
    if (unit.updatedAt) $("gps-updated").textContent = new Date(unit.updatedAt).toLocaleString("es-CO");
  } catch (error) {
    status("Sin unidad asignada", "error");
    message(error.message, true);
    $("gps-start").disabled = true;
  }
  if (!canOperate) {
    message("Ejecuta database/15_operacion_conductor.sql y vuelve a iniciar sesión para habilitar llegada y finalización.", true);
  }
  await Promise.all([loadResources(), refreshMission()]);
  $("gps-start").addEventListener("click", start);
  $("gps-stop").addEventListener("click", stop);
  $("mission-arrival").addEventListener("click", confirmArrival);
  $("mission-finish").addEventListener("click", () => showFinishModal(true));
  $("finish-cancel").addEventListener("click", () => showFinishModal(false));
  $("finish-cancel-top").addEventListener("click", () => showFinishModal(false));
  $("finish-confirm").addEventListener("click", finishAttention);
  $("driver-emergency").addEventListener("click", () => {
    if (emergencyMarker) map?.setView(emergencyMarker.getLatLng(), 16);
  });
  $("nearby-hospitals").addEventListener("click", () => {
    const list = $("nearby-hospitals-list");
    list.hidden = !list.hidden;
    if (!list.hidden) renderNearbyHospitals();
  });
  window.addEventListener("online", () => { if (tracking) { status("Buscando señal…", "idle"); publish(); } });
  window.addEventListener("offline", () => status("Sin conexión", "offline"));
}

initialize();
